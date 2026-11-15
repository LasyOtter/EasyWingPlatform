package com.easywing.platform.ai.cost;

import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成本智能优化引擎 - 自动选择最优模型，优化 Token 使用
 */
@Slf4j
@Service
public class CostOptimizationEngine {

    private final Map<String, ModelPricing> modelPricing = new HashMap<>();
    private final Map<String, CostStatistics> costStats = new ConcurrentHashMap<>();
    private final CostBudget globalBudget = new CostBudget();

    public CostOptimizationEngine() {
        initializeModelPricing();
    }

    /**
     * 初始化模型价格
     */
    private void initializeModelPricing() {
        // OpenAI 价格 (每 1M tokens)
        modelPricing.put("gpt-4o", ModelPricing.builder()
                .modelName("gpt-4o")
                .inputPricePer1M(5.0)
                .outputPricePer1M(15.0)
                .capability(95)
                .speed(8)
                .contextWindow(128000)
                .build());

        modelPricing.put("gpt-4o-mini", ModelPricing.builder()
                .modelName("gpt-4o-mini")
                .inputPricePer1M(0.15)
                .outputPricePer1M(0.6)
                .capability(85)
                .speed(9)
                .contextWindow(128000)
                .build());

        modelPricing.put("gpt-3.5-turbo", ModelPricing.builder()
                .modelName("gpt-3.5-turbo")
                .inputPricePer1M(0.5)
                .outputPricePer1M(1.5)
                .capability(75)
                .speed(10)
                .contextWindow(16000)
                .build());

        modelPricing.put("gpt-4-turbo", ModelPricing.builder()
                .modelName("gpt-4-turbo")
                .inputPricePer1M(10.0)
                .outputPricePer1M(30.0)
                .capability(98)
                .speed(7)
                .contextWindow(128000)
                .build());
    }

    /**
     * 智能选择最优模型
     */
    public String selectOptimalModel(AgentRequest request) {
        return selectOptimalModel(request, null);
    }

    /**
     * 智能选择最优模型（带预算约束）
     */
    public String selectOptimalModel(AgentRequest request, CostConstraint constraint) {
        // 分析任务复杂度
        int complexity = analyzeComplexity(request);

        // 获取可用预算
        double availableBudget = constraint != null ?
                constraint.getMaxCostPerRequest() :
                globalBudget.getRemainingBudget();

        log.info("Task complexity: {}, available budget: ${}", complexity, availableBudget);

        // 根据复杂度和预算选择模型
        if (complexity >= 9 && availableBudget >= 0.05) {
            return "gpt-4-turbo"; // 最复杂的任务
        } else if (complexity >= 7 && availableBudget >= 0.02) {
            return "gpt-4o"; // 复杂任务
        } else if (complexity >= 4 && availableBudget >= 0.005) {
            return "gpt-4o-mini"; // 中等任务
        } else {
            return "gpt-3.5-turbo"; // 简单任务
        }
    }

    /**
     * 分析任务复杂度 (0-10)
     */
    private int analyzeComplexity(AgentRequest request) {
        int complexity = 0;

        String prompt = request.getPrompt();

        // 1. 基于长度 (0-2分)
        if (prompt.length() > 1000) {
            complexity += 2;
        } else if (prompt.length() > 500) {
            complexity += 1;
        }

        // 2. 基于关键词 (0-3分)
        String lowerPrompt = prompt.toLowerCase();
        if (lowerPrompt.contains("分析") || lowerPrompt.contains("analyze")) {
            complexity += 1;
        }
        if (lowerPrompt.contains("设计") || lowerPrompt.contains("design") ||
            lowerPrompt.contains("架构") || lowerPrompt.contains("architecture")) {
            complexity += 2;
        }
        if (lowerPrompt.contains("复杂") || lowerPrompt.contains("complex")) {
            complexity += 1;
        }

        // 3. 基于工具需求 (0-2分)
        AgentRequest.AgentConfig config = request.getConfig();
        if (config != null && config.getMaxToolCalls() > 5) {
            complexity += 2;
        } else if (config != null && config.getMaxToolCalls() > 0) {
            complexity += 1;
        }

        // 4. 基于上下文 (0-2分)
        if (request.getMessages() != null && request.getMessages().size() > 3) {
            complexity += 2;
        } else if (request.getMessages() != null && request.getMessages().size() > 0) {
            complexity += 1;
        }

        // 5. 基于历史表现 (0-1分)
        CostStatistics stats = costStats.get(request.getAgentId());
        if (stats != null && stats.getAverageComplexity() > 7) {
            complexity += 1;
        }

        return Math.min(complexity, 10);
    }

    /**
     * 优化提示词以减少 Token
     */
    public String optimizePrompt(String prompt) {
        return optimizePrompt(prompt, OptimizationStrategy.BALANCED);
    }

    /**
     * 优化提示词（带策略）
     */
    public String optimizePrompt(String prompt, OptimizationStrategy strategy) {
        String optimized = prompt;

        switch (strategy) {
            case AGGRESSIVE:
                // 激进优化：最大化节省
                optimized = removeRedundancy(optimized);
                optimized = compressWhitespace(optimized);
                optimized = abbreviateCommon(optimized);
                break;

            case BALANCED:
                // 平衡优化：保持质量
                optimized = removeRedundancy(optimized);
                optimized = compressWhitespace(optimized);
                break;

            case CONSERVATIVE:
                // 保守优化：最小改动
                optimized = compressWhitespace(optimized);
                break;
        }

        int savedTokens = estimateTokens(prompt) - estimateTokens(optimized);
        if (savedTokens > 0) {
            log.info("Optimized prompt, saved ~{} tokens", savedTokens);
        }

        return optimized;
    }

    /**
     * 移除冗余内容
     */
    private String removeRedundancy(String text) {
        // 移除重复的句子
        Set<String> seen = new HashSet<>();
        StringBuilder result = new StringBuilder();

        for (String line : text.split("\n")) {
            String normalized = line.trim().toLowerCase();
            if (!seen.contains(normalized) && !normalized.isEmpty()) {
                seen.add(normalized);
                result.append(line).append("\n");
            }
        }

        return result.toString().trim();
    }

    /**
     * 压缩空白字符
     */
    private String compressWhitespace(String text) {
        return text.replaceAll("\\s+", " ")
                   .replaceAll("\n\\s+", "\n")
                   .trim();
    }

    /**
     * 缩写常见词
     */
    private String abbreviateCommon(String text) {
        return text.replace("请", "")
                   .replace("麻烦", "")
                   .replace("非常感谢", "谢谢");
    }

    /**
     * 估算 Token 数量
     */
    public int estimateTokens(String text) {
        // 简化估算：英文 ~4字符/token，中文 ~1.5字符/token
        int englishChars = text.replaceAll("[^a-zA-Z0-9\\s]", "").length();
        int chineseChars = text.replaceAll("[a-zA-Z0-9\\s]", "").length();

        return (englishChars / 4) + (int) (chineseChars / 1.5);
    }

    /**
     * 记录成本
     */
    public void recordCost(String agentId, String model, int inputTokens, int outputTokens) {
        ModelPricing pricing = modelPricing.get(model);
        if (pricing == null) {
            log.warn("Unknown model: {}", model);
            return;
        }

        double cost = pricing.calculateCost(inputTokens, outputTokens);

        // 更新统计
        CostStatistics stats = costStats.computeIfAbsent(
                agentId,
                k -> new CostStatistics(agentId)
        );
        stats.recordCost(cost, inputTokens, outputTokens);

        // 更新全局预算
        globalBudget.spend(cost);

        log.info("Cost recorded - Agent: {}, Model: {}, Tokens: {}/{}, Cost: ${:.4f}",
                agentId, model, inputTokens, outputTokens, cost);
    }

    /**
     * 获取成本报告
     */
    public CostReport getCostReport(String agentId) {
        CostStatistics stats = costStats.get(agentId);
        if (stats == null) {
            return null;
        }

        return CostReport.builder()
                .agentId(agentId)
                .totalCost(stats.getTotalCost())
                .totalRequests(stats.getTotalRequests().get())
                .totalInputTokens(stats.getTotalInputTokens())
                .totalOutputTokens(stats.getTotalOutputTokens())
                .averageCostPerRequest(stats.getAverageCostPerRequest())
                .estimatedMonthlyCost(stats.getTotalCost() * 30) // 简化估算
                .build();
    }

    /**
     * 获取优化建议
     */
    public List<OptimizationSuggestion> getOptimizationSuggestions(String agentId) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        CostStatistics stats = costStats.get(agentId);

        if (stats == null) {
            return suggestions;
        }

        // 建议1：成本过高
        if (stats.getAverageCostPerRequest() > 0.05) {
            suggestions.add(OptimizationSuggestion.builder()
                    .type("HIGH_COST")
                    .priority("HIGH")
                    .description("平均单次请求成本较高 ($" +
                            String.format("%.4f", stats.getAverageCostPerRequest()) + ")")
                    .recommendation("考虑使用更便宜的模型（如 gpt-4o-mini）或优化提示词")
                    .potentialSaving(stats.getAverageCostPerRequest() * 0.6 + " per request")
                    .build());
        }

        // 建议2：Token 使用过多
        long avgInputTokens = stats.getTotalInputTokens() / Math.max(stats.getTotalRequests().get(), 1);
        if (avgInputTokens > 2000) {
            suggestions.add(OptimizationSuggestion.builder()
                    .type("HIGH_TOKEN_USAGE")
                    .priority("MEDIUM")
                    .description("平均输入 Token 过多 (" + avgInputTokens + ")")
                    .recommendation("启用提示词优化，压缩上下文")
                    .potentialSaving("30-40% token reduction")
                    .build());
        }

        // 建议3：模型选择
        if (stats.getAverageComplexity() < 5 && stats.getPrimaryModel().equals("gpt-4o")) {
            suggestions.add(OptimizationSuggestion.builder()
                    .type("MODEL_OVERUSE")
                    .priority("MEDIUM")
                    .description("任务复杂度较低，但使用了高成本模型")
                    .recommendation("使用 gpt-4o-mini 可节省 97% 成本")
                    .potentialSaving("$" + String.format("%.4f", stats.getAverageCostPerRequest() * 0.97) +
                            " per request")
                    .build());
        }

        return suggestions;
    }

    /**
     * 设置预算
     */
    public void setBudget(double dailyBudget) {
        globalBudget.setDailyBudget(dailyBudget);
        log.info("Daily budget set to: ${}", dailyBudget);
    }

    /**
     * 检查预算
     */
    public boolean checkBudget(double requestedCost) {
        return globalBudget.canSpend(requestedCost);
    }

    /**
     * 获取预算状态
     */
    public BudgetStatus getBudgetStatus() {
        return BudgetStatus.builder()
                .dailyBudget(globalBudget.getDailyBudget())
                .spent(globalBudget.getSpent())
                .remaining(globalBudget.getRemainingBudget())
                .usagePercentage(globalBudget.getUsagePercentage())
                .build();
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPricing {
        private String modelName;
        private double inputPricePer1M;    // 每 1M input tokens 的价格
        private double outputPricePer1M;   // 每 1M output tokens 的价格
        private int capability;            // 能力评分 (0-100)
        private int speed;                 // 速度评分 (0-10)
        private int contextWindow;         // 上下文窗口大小

        public double calculateCost(int inputTokens, int outputTokens) {
            double inputCost = (inputTokens / 1_000_000.0) * inputPricePer1M;
            double outputCost = (outputTokens / 1_000_000.0) * outputPricePer1M;
            return inputCost + outputCost;
        }
    }

    @Data
    public static class CostStatistics {
        private final String agentId;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private double totalCost = 0.0;
        private long totalInputTokens = 0;
        private long totalOutputTokens = 0;
        private int totalComplexity = 0;
        private final Map<String, Integer> modelUsage = new HashMap<>();

        public CostStatistics(String agentId) {
            this.agentId = agentId;
        }

        public synchronized void recordCost(double cost, int inputTokens, int outputTokens) {
            totalRequests.incrementAndGet();
            totalCost += cost;
            totalInputTokens += inputTokens;
            totalOutputTokens += outputTokens;
        }

        public synchronized void recordComplexity(int complexity, String model) {
            totalComplexity += complexity;
            modelUsage.merge(model, 1, Integer::sum);
        }

        public double getAverageCostPerRequest() {
            long requests = totalRequests.get();
            return requests == 0 ? 0 : totalCost / requests;
        }

        public double getAverageComplexity() {
            long requests = totalRequests.get();
            return requests == 0 ? 0 : (double) totalComplexity / requests;
        }

        public String getPrimaryModel() {
            return modelUsage.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("unknown");
        }
    }

    @Data
    public static class CostBudget {
        private double dailyBudget = 10.0; // 默认 $10/天
        private final AtomicLong spent = new AtomicLong(0);
        private long lastResetTime = System.currentTimeMillis();

        public synchronized void spend(double amount) {
            checkReset();
            spent.addAndGet((long) (amount * 10000)); // 存储为 0.0001 美元单位
        }

        public synchronized boolean canSpend(double amount) {
            checkReset();
            double current = spent.get() / 10000.0;
            return (current + amount) <= dailyBudget;
        }

        public synchronized double getRemainingBudget() {
            checkReset();
            double current = spent.get() / 10000.0;
            return Math.max(0, dailyBudget - current);
        }

        public synchronized double getSpent() {
            checkReset();
            return spent.get() / 10000.0;
        }

        public double getUsagePercentage() {
            return (getSpent() / dailyBudget) * 100;
        }

        private void checkReset() {
            long now = System.currentTimeMillis();
            long dayInMs = 24 * 60 * 60 * 1000;

            if (now - lastResetTime > dayInMs) {
                spent.set(0);
                lastResetTime = now;
            }
        }

        public void setDailyBudget(double budget) {
            this.dailyBudget = budget;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostConstraint {
        private double maxCostPerRequest;
        private String preferredModel;
        private boolean allowDowngrade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostReport {
        private String agentId;
        private double totalCost;
        private long totalRequests;
        private long totalInputTokens;
        private long totalOutputTokens;
        private double averageCostPerRequest;
        private double estimatedMonthlyCost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationSuggestion {
        private String type;
        private String priority;
        private String description;
        private String recommendation;
        private String potentialSaving;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetStatus {
        private double dailyBudget;
        private double spent;
        private double remaining;
        private double usagePercentage;
    }

    public enum OptimizationStrategy {
        AGGRESSIVE,     // 激进：最大化节省
        BALANCED,       // 平衡：保持质量
        CONSERVATIVE    // 保守：最小改动
    }
}
