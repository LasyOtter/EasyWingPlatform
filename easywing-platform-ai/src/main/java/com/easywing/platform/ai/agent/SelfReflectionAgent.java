package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 自我反思 Agent - 评估和改进执行结果
 */
@Slf4j
@Component
public class SelfReflectionAgent {

    private final AgentExecutor agentExecutor;
    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    public SelfReflectionAgent(
            AgentExecutor agentExecutor, AiProviderRegistry providerRegistry, ObjectMapper objectMapper) {
        this.agentExecutor = agentExecutor;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 带反思的执行
     */
    public Mono<AgentResponse> executeWithReflection(AgentRequest request) {
        return executeWithReflection(request, 0);
    }

    /**
     * 递归反思执行
     */
    private Mono<AgentResponse> executeWithReflection(AgentRequest request, int reflectionRound) {
        int maxReflectionRounds = 2; // 最多反思 2 次

        log.info("Executing with reflection, round: {}", reflectionRound);

        return agentExecutor.execute(request).flatMap(response -> {
            // 执行反思
            return reflect(request, response).flatMap(reflection -> {
                log.info(
                        "Reflection result - score: {}, needsImprovement: {}",
                        reflection.getQualityScore(),
                        reflection.isNeedsImprovement());

                // 将反思结果添加到响应元数据
                response.getMetadata().put("reflection", reflection);

                // 如果需要改进且未达到最大轮次
                if (reflection.isNeedsImprovement() && reflectionRound < maxReflectionRounds) {

                    log.info("Retrying with improvements based on reflection");

                    // 基于反思创建改进的请求
                    AgentRequest improvedRequest = createImprovedRequest(request, response, reflection);

                    // 递归执行
                    return executeWithReflection(improvedRequest, reflectionRound + 1);
                }

                // 反思通过或达到最大轮次，返回结果
                return Mono.just(response);
            });
        });
    }

    /**
     * 反思执行结果
     */
    private Mono<Reflection> reflect(AgentRequest request, AgentResponse response) {
        String reflectionPrompt = buildReflectionPrompt(request, response);

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        ChatMessage.builder()
                                .role("system")
                                .content("你是一个严格的评审专家，负责评估 AI Agent 的执行结果。")
                                .build(),
                        ChatMessage.builder()
                                .role("user")
                                .content(reflectionPrompt)
                                .build()))
                .temperature(0.3) // 低温度，更客观
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .build();

        return providerRegistry
                .chat(completionRequest)
                .map(llmResponse -> {
                    String content =
                            llmResponse.getChoices().get(0).getMessage().getContent();
                    return parseReflection(content);
                })
                .onErrorResume(e -> {
                    log.error("Reflection failed", e);
                    // 失败时返回默认反思（认为结果可接受）
                    return Mono.just(Reflection.builder()
                            .qualityScore(70)
                            .needsImprovement(false)
                            .build());
                });
    }

    /**
     * 构建反思提示词
     */
    private String buildReflectionPrompt(AgentRequest request, AgentResponse response) {
        int iterationCount =
                response.getMetadata() != null ? (int) response.getMetadata().getOrDefault("iterationCount", 0) : 0;

        return String.format(
                """
                请评估以下 AI Agent 的执行结果：

                【原始任务】
                %s

                【执行结果】
                %s

                【执行统计】
                - 迭代次数: %d
                - 是否完成: %s
                - 完成原因: %s

                请从以下维度进行评估（1-100分）：

                1. **准确性** - 答案是否正确、完整？
                2. **相关性** - 是否回答了用户的问题？
                3. **效率** - 执行过程是否高效（迭代次数合理）？
                4. **清晰度** - 答案表述是否清晰易懂？

                请按以下 JSON 格式返回评估结果：
                ```json
                {
                  "qualityScore": 85,
                  "needsImprovement": false,
                  "issues": ["问题描述1", "问题描述2"],
                  "suggestions": ["改进建议1", "改进建议2"],
                  "reasoning": "评估理由"
                }
                ```

                评分标准：
                - 90-100: 优秀，无需改进
                - 70-89: 良好，可以接受
                - 50-69: 一般，建议改进
                - <50: 较差，必须改进
                """,
                request.getPrompt(),
                response.getMessage().getContent(),
                iterationCount,
                response.isCompleted(),
                response.getFinishReason());
    }

    /**
     * 解析反思结果
     */
    private Reflection parseReflection(String content) {
        try {
            // 提取 JSON 部分
            String json = extractJson(content);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            int score = ((Number) data.get("qualityScore")).intValue();

            return Reflection.builder()
                    .qualityScore(score)
                    .needsImprovement(score < 70) // 低于 70 分需要改进
                    .issues((List<String>) data.get("issues"))
                    .suggestions((List<String>) data.get("suggestions"))
                    .reasoning((String) data.get("reasoning"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse reflection", e);

            // 解析失败，使用简单启发式判断
            return Reflection.builder()
                    .qualityScore(estimateQuality(content))
                    .needsImprovement(content.contains("改进") || content.contains("问题"))
                    .reasoning("自动评估")
                    .build();
        }
    }

    /**
     * 提取 JSON 内容
     */
    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    /**
     * 估算质量分数（简单版本）
     */
    private int estimateQuality(String reflection) {
        if (reflection.contains("优秀") || reflection.contains("完美")) {
            return 90;
        } else if (reflection.contains("良好") || reflection.contains("可以")) {
            return 75;
        } else if (reflection.contains("一般") || reflection.contains("建议")) {
            return 60;
        } else {
            return 50;
        }
    }

    /**
     * 基于反思创建改进的请求
     */
    private AgentRequest createImprovedRequest(
            AgentRequest originalRequest, AgentResponse previousResponse, Reflection reflection) {

        // 构建改进的提示词
        String improvedPrompt = buildImprovedPrompt(originalRequest.getPrompt(), previousResponse, reflection);

        return AgentRequest.builder()
                .agentId(originalRequest.getAgentId())
                .sessionId(originalRequest.getSessionId())
                .prompt(improvedPrompt)
                .messages(originalRequest.getMessages())
                .config(originalRequest.getConfig())
                .build();
    }

    /**
     * 构建改进的提示词
     */
    private String buildImprovedPrompt(String originalPrompt, AgentResponse previousResponse, Reflection reflection) {

        StringBuilder improved = new StringBuilder();
        improved.append("【原始任务】\n");
        improved.append(originalPrompt);
        improved.append("\n\n");

        improved.append("【上次执行结果】\n");
        improved.append(previousResponse.getMessage().getContent());
        improved.append("\n\n");

        improved.append("【存在的问题】\n");
        if (reflection.getIssues() != null) {
            reflection
                    .getIssues()
                    .forEach(issue -> improved.append("- ").append(issue).append("\n"));
        }
        improved.append("\n");

        improved.append("【改进建议】\n");
        if (reflection.getSuggestions() != null) {
            reflection.getSuggestions().forEach(suggestion -> improved.append("- ")
                    .append(suggestion)
                    .append("\n"));
        }
        improved.append("\n");

        improved.append("请基于以上反馈，重新完成任务，提供更准确、更完整的答案。");

        return improved.toString();
    }

    /**
     * 反思结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reflection {
        /**
         * 质量分数 (0-100)
         */
        private int qualityScore;

        /**
         * 是否需要改进
         */
        private boolean needsImprovement;

        /**
         * 发现的问题
         */
        private List<String> issues;

        /**
         * 改进建议
         */
        private List<String> suggestions;

        /**
         * 评估理由
         */
        private String reasoning;
    }
}
