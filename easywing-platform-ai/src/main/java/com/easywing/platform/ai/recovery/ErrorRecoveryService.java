package com.easywing.platform.ai.recovery;

import com.easywing.platform.ai.agent.AgentExecutor;
import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

/**
 * 智能错误恢复服务 - 自动诊断和恢复错误
 */
@Slf4j
@Service
public class ErrorRecoveryService {

    private final AgentExecutor agentExecutor;
    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, ErrorPattern> knownErrors = new HashMap<>();

    public ErrorRecoveryService(
            AgentExecutor agentExecutor,
            AiProviderRegistry providerRegistry,
            ObjectMapper objectMapper) {
        this.agentExecutor = agentExecutor;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
        initializeErrorPatterns();
    }

    /**
     * 初始化已知错误模式
     */
    private void initializeErrorPatterns() {
        // 参数错误
        knownErrors.put("PARAMETER_ERROR", ErrorPattern.builder()
                .type("PARAMETER_ERROR")
                .keywords(List.of("参数", "缺少", "无效", "parameter", "invalid"))
                .severity(ErrorSeverity.MEDIUM)
                .recoveryStrategy(RecoveryStrategy.FIX_PARAMETERS)
                .maxRetries(2)
                .build());

        // 工具不可用
        knownErrors.put("TOOL_UNAVAILABLE", ErrorPattern.builder()
                .type("TOOL_UNAVAILABLE")
                .keywords(List.of("工具未找到", "tool not found", "不可用"))
                .severity(ErrorSeverity.HIGH)
                .recoveryStrategy(RecoveryStrategy.USE_ALTERNATIVE)
                .maxRetries(1)
                .build());

        // 超时错误
        knownErrors.put("TIMEOUT", ErrorPattern.builder()
                .type("TIMEOUT")
                .keywords(List.of("超时", "timeout", "time out"))
                .severity(ErrorSeverity.HIGH)
                .recoveryStrategy(RecoveryStrategy.RETRY_WITH_BACKOFF)
                .maxRetries(3)
                .build());

        // API 限流
        knownErrors.put("RATE_LIMIT", ErrorPattern.builder()
                .type("RATE_LIMIT")
                .keywords(List.of("限流", "rate limit", "too many requests"))
                .severity(ErrorSeverity.HIGH)
                .recoveryStrategy(RecoveryStrategy.EXPONENTIAL_BACKOFF)
                .maxRetries(5)
                .build());

        // 解析错误
        knownErrors.put("PARSE_ERROR", ErrorPattern.builder()
                .type("PARSE_ERROR")
                .keywords(List.of("解析", "parse", "format", "json"))
                .severity(ErrorSeverity.MEDIUM)
                .recoveryStrategy(RecoveryStrategy.SIMPLIFY_OUTPUT)
                .maxRetries(2)
                .build());
    }

    /**
     * 带错误恢复的执行
     */
    public Mono<AgentResponse> executeWithRecovery(AgentRequest request) {
        return executeWithRecovery(request, 0, new ArrayList<>());
    }

    /**
     * 递归错误恢复执行
     */
    private Mono<AgentResponse> executeWithRecovery(
            AgentRequest request,
            int attemptNumber,
            List<RecoveryAttempt> attempts) {

        log.info("Executing with recovery, attempt: {}", attemptNumber + 1);

        return agentExecutor.execute(request)
                .onErrorResume(error -> {
                    log.error("Execution failed, analyzing error", error);

                    // 诊断错误
                    return diagnoseError(error)
                            .flatMap(diagnosis -> {
                                log.info("Error diagnosed: type={}, severity={}",
                                        diagnosis.getErrorType(),
                                        diagnosis.getSeverity());

                                // 记录恢复尝试
                                RecoveryAttempt attempt = RecoveryAttempt.builder()
                                        .attemptNumber(attemptNumber)
                                        .errorType(diagnosis.getErrorType())
                                        .errorMessage(error.getMessage())
                                        .diagnosis(diagnosis)
                                        .timestamp(System.currentTimeMillis())
                                        .build();
                                attempts.add(attempt);

                                // 获取错误模式
                                ErrorPattern pattern = knownErrors.get(diagnosis.getErrorType());

                                // 检查是否超过最大重试次数
                                if (pattern != null && attemptNumber >= pattern.getMaxRetries()) {
                                    log.error("Max retries exceeded for error type: {}",
                                            diagnosis.getErrorType());
                                    return Mono.error(new MaxRetriesExceededException(
                                            "最大重试次数已达到", attempts));
                                }

                                // 应用恢复策略
                                return applyRecoveryStrategy(request, diagnosis, attempts)
                                        .flatMap(recoveredRequest -> {
                                            // 递归重试
                                            return executeWithRecovery(
                                                    recoveredRequest,
                                                    attemptNumber + 1,
                                                    attempts);
                                        });
                            });
                })
                .doOnSuccess(response -> {
                    // 成功后记录恢复信息
                    if (!attempts.isEmpty()) {
                        Map<String, Object> metadata = response.getMetadata();
                        if (metadata == null) {
                            metadata = new HashMap<>();
                            response.setMetadata(metadata);
                        }
                        metadata.put("recoveryAttempts", attempts);
                        metadata.put("recoveredAfterAttempts", attemptNumber);
                        log.info("Successfully recovered after {} attempts", attemptNumber);
                    }
                });
    }

    /**
     * 诊断错误
     */
    private Mono<ErrorDiagnosis> diagnoseError(Throwable error) {
        String errorMessage = error.getMessage();
        String errorClass = error.getClass().getSimpleName();

        // 匹配已知错误模式
        for (ErrorPattern pattern : knownErrors.values()) {
            if (matchesPattern(errorMessage, pattern)) {
                return Mono.just(ErrorDiagnosis.builder()
                        .errorType(pattern.getType())
                        .severity(pattern.getSeverity())
                        .rootCause(identifyRootCause(error, pattern))
                        .suggestedFix(generateSuggestedFix(pattern))
                        .recoverable(true)
                        .build());
            }
        }

        // 未知错误，使用 LLM 分析
        return analyzeErrorWithLLM(errorMessage, errorClass);
    }

    /**
     * 匹配错误模式
     */
    private boolean matchesPattern(String errorMessage, ErrorPattern pattern) {
        if (errorMessage == null) return false;
        String lowerMessage = errorMessage.toLowerCase();
        return pattern.getKeywords().stream()
                .anyMatch(keyword -> lowerMessage.contains(keyword.toLowerCase()));
    }

    /**
     * 识别根本原因
     */
    private String identifyRootCause(Throwable error, ErrorPattern pattern) {
        // 简化实现
        return switch (pattern.getType()) {
            case "PARAMETER_ERROR" -> "参数验证失败或缺少必需参数";
            case "TOOL_UNAVAILABLE" -> "请求的工具不存在或未注册";
            case "TIMEOUT" -> "操作执行时间超过限制";
            case "RATE_LIMIT" -> "API 调用频率超过限制";
            case "PARSE_ERROR" -> "响应格式无法解析";
            default -> "未知错误";
        };
    }

    /**
     * 生成修复建议
     */
    private String generateSuggestedFix(ErrorPattern pattern) {
        return switch (pattern.getRecoveryStrategy()) {
            case FIX_PARAMETERS -> "修正参数格式和内容";
            case USE_ALTERNATIVE -> "使用替代工具或方法";
            case RETRY_WITH_BACKOFF -> "延迟后重试";
            case EXPONENTIAL_BACKOFF -> "指数退避后重试";
            case SIMPLIFY_OUTPUT -> "简化输出格式要求";
        };
    }

    /**
     * 使用 LLM 分析错误
     */
    private Mono<ErrorDiagnosis> analyzeErrorWithLLM(String errorMessage, String errorClass) {
        String analysisPrompt = String.format("""
                请分析以下错误：

                错误类型: %s
                错误信息: %s

                请判断：
                1. 错误类型（PARAMETER_ERROR/TOOL_UNAVAILABLE/TIMEOUT/RATE_LIMIT/PARSE_ERROR/UNKNOWN）
                2. 严重程度（LOW/MEDIUM/HIGH/CRITICAL）
                3. 是否可恢复
                4. 建议的修复方案

                返回 JSON 格式：
                {
                  "errorType": "PARAMETER_ERROR",
                  "severity": "MEDIUM",
                  "recoverable": true,
                  "rootCause": "根本原因",
                  "suggestedFix": "修复建议"
                }
                """, errorClass, errorMessage);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.builder()
                        .role("user")
                        .content(analysisPrompt)
                        .build()))
                .temperature(0.3)
                .build();

        return providerRegistry.chat(request)
                .map(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    return parseDiagnosis(content);
                })
                .onErrorReturn(ErrorDiagnosis.builder()
                        .errorType("UNKNOWN")
                        .severity(ErrorSeverity.MEDIUM)
                        .recoverable(false)
                        .rootCause("无法分析错误")
                        .build());
    }

    /**
     * 解析诊断结果
     */
    private ErrorDiagnosis parseDiagnosis(String content) {
        try {
            String json = extractJson(content);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            return ErrorDiagnosis.builder()
                    .errorType((String) data.get("errorType"))
                    .severity(ErrorSeverity.valueOf((String) data.get("severity")))
                    .recoverable((Boolean) data.getOrDefault("recoverable", false))
                    .rootCause((String) data.get("rootCause"))
                    .suggestedFix((String) data.get("suggestedFix"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse diagnosis", e);
            return ErrorDiagnosis.builder()
                    .errorType("UNKNOWN")
                    .severity(ErrorSeverity.MEDIUM)
                    .recoverable(false)
                    .build();
        }
    }

    /**
     * 应用恢复策略
     */
    private Mono<AgentRequest> applyRecoveryStrategy(
            AgentRequest originalRequest,
            ErrorDiagnosis diagnosis,
            List<RecoveryAttempt> attempts) {

        ErrorPattern pattern = knownErrors.get(diagnosis.getErrorType());
        if (pattern == null) {
            return Mono.error(new UnrecoverableErrorException(
                    "未知错误类型，无法恢复", diagnosis));
        }

        return switch (pattern.getRecoveryStrategy()) {
            case FIX_PARAMETERS -> fixParameters(originalRequest, diagnosis);
            case USE_ALTERNATIVE -> useAlternative(originalRequest, diagnosis);
            case RETRY_WITH_BACKOFF -> retryWithBackoff(originalRequest, attempts.size());
            case EXPONENTIAL_BACKOFF -> exponentialBackoff(originalRequest, attempts.size());
            case SIMPLIFY_OUTPUT -> simplifyOutput(originalRequest);
        };
    }

    /**
     * 修正参数
     */
    private Mono<AgentRequest> fixParameters(AgentRequest request, ErrorDiagnosis diagnosis) {
        // 简化实现：添加更明确的参数说明
        String improvedPrompt = request.getPrompt() + "\n\n注意：请提供所有必需的参数。";

        return Mono.just(AgentRequest.builder()
                .agentId(request.getAgentId())
                .sessionId(request.getSessionId())
                .prompt(improvedPrompt)
                .messages(request.getMessages())
                .config(request.getConfig())
                .build());
    }

    /**
     * 使用替代方案
     */
    private Mono<AgentRequest> useAlternative(AgentRequest request, ErrorDiagnosis diagnosis) {
        // 简化实现：降级到简单模式
        AgentRequest.AgentConfig config = request.getConfig();
        if (config != null) {
            config.setMaxToolCalls(0); // 禁用工具
        }

        return Mono.just(request);
    }

    /**
     * 延迟重试
     */
    private Mono<AgentRequest> retryWithBackoff(AgentRequest request, int attemptNumber) {
        return Mono.just(request)
                .delayElement(Duration.ofSeconds(attemptNumber + 1));
    }

    /**
     * 指数退避
     */
    private Mono<AgentRequest> exponentialBackoff(AgentRequest request, int attemptNumber) {
        long delayMs = (long) (Math.pow(2, attemptNumber) * 1000);
        return Mono.just(request)
                .delayElement(Duration.ofMillis(delayMs));
    }

    /**
     * 简化输出
     */
    private Mono<AgentRequest> simplifyOutput(AgentRequest request) {
        String simplifiedPrompt = request.getPrompt() +
                "\n\n请用简单的文本格式回答，不需要 JSON 或其他结构化格式。";

        return Mono.just(AgentRequest.builder()
                .agentId(request.getAgentId())
                .sessionId(request.getSessionId())
                .prompt(simplifiedPrompt)
                .messages(request.getMessages())
                .config(request.getConfig())
                .build());
    }

    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorPattern {
        private String type;
        private List<String> keywords;
        private ErrorSeverity severity;
        private RecoveryStrategy recoveryStrategy;
        private int maxRetries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDiagnosis {
        private String errorType;
        private ErrorSeverity severity;
        private String rootCause;
        private String suggestedFix;
        private boolean recoverable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecoveryAttempt {
        private int attemptNumber;
        private String errorType;
        private String errorMessage;
        private ErrorDiagnosis diagnosis;
        private long timestamp;
    }

    public enum ErrorSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum RecoveryStrategy {
        FIX_PARAMETERS,
        USE_ALTERNATIVE,
        RETRY_WITH_BACKOFF,
        EXPONENTIAL_BACKOFF,
        SIMPLIFY_OUTPUT
    }

    public static class MaxRetriesExceededException extends RuntimeException {
        private final List<RecoveryAttempt> attempts;

        public MaxRetriesExceededException(String message, List<RecoveryAttempt> attempts) {
            super(message);
            this.attempts = attempts;
        }

        public List<RecoveryAttempt> getAttempts() {
            return attempts;
        }
    }

    public static class UnrecoverableErrorException extends RuntimeException {
        private final ErrorDiagnosis diagnosis;

        public UnrecoverableErrorException(String message, ErrorDiagnosis diagnosis) {
            super(message);
            this.diagnosis = diagnosis;
        }

        public ErrorDiagnosis getDiagnosis() {
            return diagnosis;
        }
    }
}
