package com.easywing.platform.ai.optimization;

import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 提示词优化器 - 根据历史执行数据自动优化提示词
 */
@Slf4j
@Service
public class PromptOptimizer {

    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, PromptPerformanceHistory> performanceHistory = new ConcurrentHashMap<>();

    public PromptOptimizer(AiProviderRegistry providerRegistry, ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 优化提示词
     */
    public Mono<OptimizedPrompt> optimizePrompt(String originalPrompt, String context) {
        log.info("Optimizing prompt: {}", originalPrompt);

        // 获取历史表现
        PromptPerformanceHistory history = getHistory(originalPrompt);

        return analyzePrompt(originalPrompt, context, history)
                .flatMap(analysis -> generateOptimizedPrompt(originalPrompt, analysis))
                .map(optimized -> {
                    log.info(
                            "Prompt optimized, improvements: {}",
                            optimized.getImprovements().size());
                    return optimized;
                });
    }

    /**
     * 记录执行结果
     */
    public void recordExecution(String prompt, AgentResponse response, boolean success) {
        PromptPerformanceHistory history =
                performanceHistory.computeIfAbsent(normalizePrompt(prompt), k -> new PromptPerformanceHistory());

        ExecutionRecord record = ExecutionRecord.builder()
                .prompt(prompt)
                .success(success)
                .iterationCount(extractIterationCount(response))
                .executionTime(extractExecutionTime(response))
                .timestamp(System.currentTimeMillis())
                .build();

        history.addRecord(record);
    }

    /**
     * 分析提示词
     */
    private Mono<PromptAnalysis> analyzePrompt(String prompt, String context, PromptPerformanceHistory history) {

        String analysisPrompt = buildAnalysisPrompt(prompt, context, history);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        ChatMessage.builder()
                                .role("system")
                                .content("你是提示词工程专家，擅长优化 AI 提示词以提高效果。")
                                .build(),
                        ChatMessage.builder()
                                .role("user")
                                .content(analysisPrompt)
                                .build()))
                .temperature(0.3)
                .build();

        return providerRegistry
                .chat(request)
                .map(response ->
                        parseAnalysis(response.getChoices().get(0).getMessage().getContent()));
    }

    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(String prompt, String context, PromptPerformanceHistory history) {

        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下提示词并提出优化建议：\n\n");
        sb.append("【原始提示词】\n");
        sb.append(prompt).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("【使用场景】\n");
            sb.append(context).append("\n\n");
        }

        if (history != null && !history.getRecords().isEmpty()) {
            sb.append("【历史表现】\n");
            sb.append(String.format("- 执行次数: %d\n", history.getRecords().size()));
            sb.append(String.format("- 成功率: %.1f%%\n", history.getSuccessRate() * 100));
            sb.append(String.format("- 平均迭代次数: %.1f\n", history.getAverageIterations()));
            sb.append("\n");
        }

        sb.append(
                """
                请从以下维度分析：
                1. 清晰度 - 指令是否明确？
                2. 完整性 - 是否包含必要信息？
                3. 结构化 - 是否有良好的组织？
                4. 示例 - 是否需要添加示例？
                5. 约束 - 是否需要添加约束条件？

                返回 JSON 格式：
                {
                  "issues": ["问题1", "问题2"],
                  "suggestions": ["建议1", "建议2"],
                  "score": 75
                }
                """);

        return sb.toString();
    }

    /**
     * 生成优化后的提示词
     */
    private Mono<OptimizedPrompt> generateOptimizedPrompt(String originalPrompt, PromptAnalysis analysis) {

        if (analysis.getScore() >= 85) {
            // 分数足够高，不需要优化
            return Mono.just(OptimizedPrompt.builder()
                    .originalPrompt(originalPrompt)
                    .optimizedPrompt(originalPrompt)
                    .improvements(List.of())
                    .score(analysis.getScore())
                    .build());
        }

        String optimizationPrompt = buildOptimizationPrompt(originalPrompt, analysis);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.builder()
                        .role("user")
                        .content(optimizationPrompt)
                        .build()))
                .temperature(0.5)
                .build();

        return providerRegistry.chat(request).map(response -> {
            String optimized = response.getChoices().get(0).getMessage().getContent();

            return OptimizedPrompt.builder()
                    .originalPrompt(originalPrompt)
                    .optimizedPrompt(optimized)
                    .improvements(analysis.getSuggestions())
                    .score(analysis.getScore())
                    .build();
        });
    }

    /**
     * 构建优化提示
     */
    private String buildOptimizationPrompt(String originalPrompt, PromptAnalysis analysis) {
        return String.format(
                """
                请优化以下提示词：

                【原始提示词】
                %s

                【发现的问题】
                %s

                【优化建议】
                %s

                请根据以上分析，重写提示词，使其：
                1. 更清晰明确
                2. 包含必要的上下文和约束
                3. 有良好的结构
                4. 包含示例（如果需要）

                直接返回优化后的提示词，不要解释。
                """,
                originalPrompt, String.join("\n", analysis.getIssues()), String.join("\n", analysis.getSuggestions()));
    }

    /**
     * 解析分析结果
     */
    private PromptAnalysis parseAnalysis(String content) {
        try {
            String json = extractJson(content);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            return PromptAnalysis.builder()
                    .issues((List<String>) data.get("issues"))
                    .suggestions((List<String>) data.get("suggestions"))
                    .score(((Number) data.get("score")).intValue())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse analysis", e);
            return PromptAnalysis.builder()
                    .issues(List.of())
                    .suggestions(List.of())
                    .score(70)
                    .build();
        }
    }

    /**
     * 获取历史记录
     */
    private PromptPerformanceHistory getHistory(String prompt) {
        String normalized = normalizePrompt(prompt);
        return performanceHistory.get(normalized);
    }

    /**
     * 标准化提示词（用于匹配）
     */
    private String normalizePrompt(String prompt) {
        return prompt.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * 提取迭代次数
     */
    private int extractIterationCount(AgentResponse response) {
        if (response.getMetadata() != null) {
            Object count = response.getMetadata().get("iterationCount");
            if (count instanceof Number) {
                return ((Number) count).intValue();
            }
        }
        return 0;
    }

    /**
     * 提取执行时间
     */
    private long extractExecutionTime(AgentResponse response) {
        if (response.getMetadata() != null) {
            Object time = response.getMetadata().get("totalExecutionTime");
            if (time instanceof Number) {
                return ((Number) time).longValue();
            }
        }
        return 0;
    }

    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    /**
     * 获取优化建议
     */
    public Mono<List<String>> getSuggestions(String prompt) {
        return analyzePrompt(prompt, null, null).map(PromptAnalysis::getSuggestions);
    }

    /**
     * 获取最佳提示词模板
     */
    public Map<String, PromptTemplate> getBestTemplates() {
        Map<String, PromptTemplate> templates = new HashMap<>();

        // 任务规划模板
        templates.put(
                "planning",
                PromptTemplate.builder()
                        .name("任务规划")
                        .template(
                                """
                        任务: {task}

                        请制定详细的执行计划，包括：
                        1. 目标分解
                        2. 执行步骤
                        3. 所需资源
                        4. 时间估算

                        返回结构化的计划。
                        """)
                        .variables(List.of("task"))
                        .category("planning")
                        .build());

        // 代码生成模板
        templates.put(
                "code_generation",
                PromptTemplate.builder()
                        .name("代码生成")
                        .template(
                                """
                        需求: {requirement}
                        编程语言: {language}

                        请生成完整的代码实现，包括：
                        1. 必要的注释
                        2. 错误处理
                        3. 测试用例

                        代码要求：
                        - 遵循最佳实践
                        - 代码清晰易读
                        - 考虑边界情况
                        """)
                        .variables(List.of("requirement", "language"))
                        .category("coding")
                        .build());

        return templates;
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizedPrompt {
        private String originalPrompt;
        private String optimizedPrompt;
        private List<String> improvements;
        private int score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptAnalysis {
        private List<String> issues;
        private List<String> suggestions;
        private int score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionRecord {
        private String prompt;
        private boolean success;
        private int iterationCount;
        private long executionTime;
        private long timestamp;
    }

    @Data
    public static class PromptPerformanceHistory {
        private final List<ExecutionRecord> records = new ArrayList<>();

        public void addRecord(ExecutionRecord record) {
            records.add(record);
            // 只保留最近 100 条
            if (records.size() > 100) {
                records.remove(0);
            }
        }

        public double getSuccessRate() {
            if (records.isEmpty()) return 0.0;
            long successCount =
                    records.stream().filter(ExecutionRecord::isSuccess).count();
            return (double) successCount / records.size();
        }

        public double getAverageIterations() {
            if (records.isEmpty()) return 0.0;
            return records.stream()
                    .mapToInt(ExecutionRecord::getIterationCount)
                    .average()
                    .orElse(0.0);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptTemplate {
        private String name;
        private String template;
        private List<String> variables;
        private String category;
        private String description;
    }
}
