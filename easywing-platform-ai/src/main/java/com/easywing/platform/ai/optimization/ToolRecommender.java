package com.easywing.platform.ai.optimization;

import com.easywing.platform.ai.rag.EmbeddingService;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工具推荐系统 - 基于任务智能推荐相关工具
 */
@Slf4j
@Service
public class ToolRecommender {

    private final ToolExecutor toolExecutor;
    private final EmbeddingService embeddingService;
    private final Map<String, ToolUsageStats> usageStats = new ConcurrentHashMap<>();
    private final Map<String, float[]> toolEmbeddings = new ConcurrentHashMap<>();

    public ToolRecommender(ToolExecutor toolExecutor, EmbeddingService embeddingService) {
        this.toolExecutor = toolExecutor;
        this.embeddingService = embeddingService;
        initializeToolEmbeddings();
    }

    /**
     * 初始化工具嵌入向量
     */
    private void initializeToolEmbeddings() {
        List<ToolDefinition> tools = toolExecutor.getAvailableTools();
        log.info("Initializing embeddings for {} tools", tools.size());

        tools.forEach(tool -> {
            String toolText = tool.getName() + " " + tool.getDescription();
            embeddingService.embed(toolText).subscribe(embedding -> {
                toolEmbeddings.put(tool.getName(), embedding);
                log.debug("Initialized embedding for tool: {}", tool.getName());
            });
        });
    }

    /**
     * 推荐工具
     */
    public Mono<List<ToolRecommendation>> recommendTools(String task, int topK) {
        log.info("Recommending tools for task: {}", task);

        return embeddingService.embed(task).map(taskEmbedding -> {
            List<ToolDefinition> allTools = toolExecutor.getAvailableTools();

            return allTools.stream()
                    .map(tool -> {
                        // 计算相似度分数
                        double similarityScore = calculateSimilarity(taskEmbedding, toolEmbeddings.get(tool.getName()));

                        // 获取使用统计
                        ToolUsageStats stats = usageStats.get(tool.getName());

                        // 计算综合得分
                        double finalScore = calculateFinalScore(similarityScore, stats, tool);

                        return ToolRecommendation.builder()
                                .tool(tool)
                                .score(finalScore)
                                .similarityScore(similarityScore)
                                .usageCount(stats != null ? stats.getTotalUsages() : 0)
                                .successRate(stats != null ? stats.getSuccessRate() : 0.0)
                                .reason(generateReason(tool, similarityScore, stats))
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(ToolRecommendation::getScore)
                            .reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
        });
    }

    /**
     * 基于关键词推荐
     */
    public List<ToolRecommendation> recommendByKeywords(List<String> keywords, int topK) {
        log.info("Recommending tools by keywords: {}", keywords);

        List<ToolDefinition> allTools = toolExecutor.getAvailableTools();

        return allTools.stream()
                .map(tool -> {
                    double keywordScore = calculateKeywordScore(tool, keywords);
                    ToolUsageStats stats = usageStats.get(tool.getName());

                    double finalScore = keywordScore * 0.7 + (stats != null ? stats.getSuccessRate() * 30 : 0);

                    return ToolRecommendation.builder()
                            .tool(tool)
                            .score(finalScore)
                            .similarityScore(keywordScore)
                            .usageCount(stats != null ? stats.getTotalUsages() : 0)
                            .successRate(stats != null ? stats.getSuccessRate() : 0.0)
                            .reason("关键词匹配")
                            .build();
                })
                .filter(r -> r.getScore() > 20)
                .sorted(Comparator.comparingDouble(ToolRecommendation::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 基于历史推荐（协同过滤）
     */
    public List<ToolRecommendation> recommendByHistory(String userId, int topK) {
        log.info("Recommending tools based on history for user: {}", userId);

        // 简化实现：推荐最常用的工具
        return usageStats.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().getSuccessRate() * b.getValue().getTotalUsages(),
                        a.getValue().getSuccessRate() * a.getValue().getTotalUsages()))
                .limit(topK)
                .map(entry -> {
                    ToolDefinition tool = toolExecutor.getAvailableTools().stream()
                            .filter(t -> t.getName().equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);

                    if (tool == null) return null;

                    ToolUsageStats stats = entry.getValue();

                    return ToolRecommendation.builder()
                            .tool(tool)
                            .score(stats.getSuccessRate() * stats.getTotalUsages() / 100.0)
                            .usageCount(stats.getTotalUsages())
                            .successRate(stats.getSuccessRate())
                            .reason("基于使用历史")
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 记录工具使用
     */
    public void recordUsage(String toolName, boolean success, long executionTime) {
        ToolUsageStats stats = usageStats.computeIfAbsent(toolName, k -> new ToolUsageStats());

        stats.recordUsage(success, executionTime);
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            return 0.0;
        }

        if (embedding1.length != embedding2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 计算关键词得分
     */
    private double calculateKeywordScore(ToolDefinition tool, List<String> keywords) {
        String toolText = (tool.getName() + " " + tool.getDescription()).toLowerCase();

        long matchCount = keywords.stream()
                .filter(keyword -> toolText.contains(keyword.toLowerCase()))
                .count();

        return (double) matchCount / keywords.size() * 100;
    }

    /**
     * 计算最终得分
     */
    private double calculateFinalScore(double similarityScore, ToolUsageStats stats, ToolDefinition tool) {

        // 基础分：相似度 (0-100)
        double score = similarityScore * 100;

        // 成功率加权 (0-30)
        if (stats != null && stats.getTotalUsages() > 5) {
            score += stats.getSuccessRate() * 30;
        } else {
            // 新工具给予中等分数
            score += 15;
        }

        // 使用频率加权 (0-20)
        if (stats != null) {
            double usageBonus = Math.min(stats.getTotalUsages() / 10.0, 20.0);
            score += usageBonus;
        }

        return score;
    }

    /**
     * 生成推荐理由
     */
    private String generateReason(ToolDefinition tool, double similarityScore, ToolUsageStats stats) {

        List<String> reasons = new ArrayList<>();

        if (similarityScore > 0.8) {
            reasons.add("高度相关");
        } else if (similarityScore > 0.6) {
            reasons.add("相关");
        }

        if (stats != null) {
            if (stats.getSuccessRate() > 0.9) {
                reasons.add("高成功率");
            }

            if (stats.getTotalUsages() > 100) {
                reasons.add("使用广泛");
            }
        }

        if (reasons.isEmpty()) {
            return "可能有帮助";
        }

        return String.join("，", reasons);
    }

    /**
     * 获取工具统计
     */
    public Map<String, ToolUsageStats> getUsageStats() {
        return new HashMap<>(usageStats);
    }

    /**
     * 获取热门工具
     */
    public List<ToolRecommendation> getPopularTools(int topK) {
        return usageStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue().getTotalUsages(), a.getValue().getTotalUsages()))
                .limit(topK)
                .map(entry -> {
                    ToolDefinition tool = toolExecutor.getAvailableTools().stream()
                            .filter(t -> t.getName().equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);

                    if (tool == null) return null;

                    ToolUsageStats stats = entry.getValue();

                    return ToolRecommendation.builder()
                            .tool(tool)
                            .score(stats.getTotalUsages())
                            .usageCount(stats.getTotalUsages())
                            .successRate(stats.getSuccessRate())
                            .reason("热门工具")
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolRecommendation {
        private ToolDefinition tool;
        private double score;
        private double similarityScore;
        private int usageCount;
        private double successRate;
        private String reason;
    }

    @Data
    public static class ToolUsageStats {
        private int totalUsages = 0;
        private int successfulUsages = 0;
        private int failedUsages = 0;
        private long totalExecutionTime = 0;
        private long lastUsedTimestamp = 0;

        public void recordUsage(boolean success, long executionTime) {
            totalUsages++;
            if (success) {
                successfulUsages++;
            } else {
                failedUsages++;
            }
            totalExecutionTime += executionTime;
            lastUsedTimestamp = System.currentTimeMillis();
        }

        public double getSuccessRate() {
            if (totalUsages == 0) return 0.0;
            return (double) successfulUsages / totalUsages;
        }

        public double getAverageExecutionTime() {
            if (totalUsages == 0) return 0.0;
            return (double) totalExecutionTime / totalUsages;
        }
    }
}
