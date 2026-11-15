package com.easywing.platform.ai.testing;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A/B 测试框架 - 支持策略、提示词、配置的对比测试
 */
@Slf4j
@Service
public class ABTestingFramework {

    private final Map<String, ABTest> activeTests = new ConcurrentHashMap<>();
    private final Map<String, TestStatistics> statistics = new ConcurrentHashMap<>();

    /**
     * 创建 A/B 测试
     */
    public String createTest(ABTestConfig config) {
        String testId = UUID.randomUUID().toString();

        ABTest test = ABTest.builder()
                .testId(testId)
                .name(config.getName())
                .description(config.getDescription())
                .variants(config.getVariants())
                .trafficSplit(config.getTrafficSplit())
                .metrics(config.getMetrics())
                .status(TestStatus.RUNNING)
                .startTime(System.currentTimeMillis())
                .build();

        activeTests.put(testId, test);

        // 初始化统计
        for (TestVariant variant : config.getVariants()) {
            statistics.put(testId + ":" + variant.getId(), new TestStatistics(variant.getId()));
        }

        log.info("Created A/B test: {} with {} variants", testId, config.getVariants().size());
        return testId;
    }

    /**
     * 选择变体（基于流量分配）
     */
    public TestVariant selectVariant(String testId, String userId) {
        ABTest test = activeTests.get(testId);
        if (test == null || test.getStatus() != TestStatus.RUNNING) {
            return null;
        }

        // 基于用户ID的一致性哈希
        int hash = Math.abs(userId.hashCode());
        double random = (hash % 10000) / 10000.0;

        double cumulative = 0.0;
        for (int i = 0; i < test.getVariants().size(); i++) {
            cumulative += test.getTrafficSplit().get(i);
            if (random < cumulative) {
                return test.getVariants().get(i);
            }
        }

        return test.getVariants().get(test.getVariants().size() - 1);
    }

    /**
     * 记录测试结果
     */
    public void recordResult(String testId, String variantId, TestResult result) {
        String key = testId + ":" + variantId;
        TestStatistics stats = statistics.get(key);

        if (stats != null) {
            stats.recordResult(result);
            log.debug("Recorded result for test {}, variant {}: success={}, duration={}ms",
                    testId, variantId, result.isSuccess(), result.getExecutionTime());
        }
    }

    /**
     * 获取测试报告
     */
    public TestReport getReport(String testId) {
        ABTest test = activeTests.get(testId);
        if (test == null) {
            return null;
        }

        List<VariantReport> variantReports = new ArrayList<>();

        for (TestVariant variant : test.getVariants()) {
            String key = testId + ":" + variant.getId();
            TestStatistics stats = statistics.get(key);

            if (stats != null) {
                variantReports.add(VariantReport.builder()
                        .variantId(variant.getId())
                        .variantName(variant.getName())
                        .totalExecutions(stats.getTotalExecutions().get())
                        .successRate(stats.getSuccessRate())
                        .avgExecutionTime(stats.getAverageExecutionTime())
                        .avgAccuracy(stats.getAverageAccuracy())
                        .avgUserSatisfaction(stats.getAverageUserSatisfaction())
                        .build());
            }
        }

        // 计算统计显著性
        String winner = determineWinner(variantReports);

        return TestReport.builder()
                .testId(testId)
                .testName(test.getName())
                .status(test.getStatus())
                .variantReports(variantReports)
                .winner(winner)
                .statisticalSignificance(calculateSignificance(variantReports))
                .startTime(test.getStartTime())
                .endTime(test.getEndTime())
                .build();
    }

    /**
     * 停止测试
     */
    public void stopTest(String testId) {
        ABTest test = activeTests.get(testId);
        if (test != null) {
            test.setStatus(TestStatus.STOPPED);
            test.setEndTime(System.currentTimeMillis());
            log.info("Stopped A/B test: {}", testId);
        }
    }

    /**
     * 完成测试并应用获胜变体
     */
    public Mono<String> completeTest(String testId) {
        TestReport report = getReport(testId);
        if (report == null) {
            return Mono.error(new IllegalArgumentException("Test not found: " + testId));
        }

        stopTest(testId);

        String winner = report.getWinner();
        if (winner != null) {
            log.info("Test {} completed. Winner: {}", testId, winner);
            return Mono.just(winner);
        }

        return Mono.just("No clear winner");
    }

    /**
     * 确定获胜者
     */
    private String determineWinner(List<VariantReport> reports) {
        if (reports.size() < 2) {
            return null;
        }

        // 综合评分：成功率 40% + 准确率 30% + 用户满意度 20% + 速度 10%
        VariantReport winner = reports.stream()
                .max(Comparator.comparingDouble(r -> {
                    double score = 0.0;
                    score += r.getSuccessRate() * 40;
                    score += r.getAvgAccuracy() * 30;
                    score += r.getAvgUserSatisfaction() * 20;

                    // 速度得分（越快越好）
                    double speedScore = Math.max(0, 10 - r.getAvgExecutionTime() / 1000.0);
                    score += speedScore;

                    return score;
                }))
                .orElse(null);

        return winner != null ? winner.getVariantId() : null;
    }

    /**
     * 计算统计显著性
     */
    private double calculateSignificance(List<VariantReport> reports) {
        if (reports.size() < 2) {
            return 0.0;
        }

        // 简化实现：基于样本量和成功率差异
        long minSampleSize = reports.stream()
                .mapToLong(VariantReport::getTotalExecutions)
                .min()
                .orElse(0);

        if (minSampleSize < 30) {
            return 0.0; // 样本量太小
        }

        // 计算成功率差异
        double maxSuccessRate = reports.stream()
                .mapToDouble(VariantReport::getSuccessRate)
                .max()
                .orElse(0);

        double minSuccessRate = reports.stream()
                .mapToDouble(VariantReport::getSuccessRate)
                .min()
                .orElse(0);

        double difference = maxSuccessRate - minSuccessRate;

        // 简化的统计显著性计算
        if (difference > 0.1 && minSampleSize > 100) {
            return 0.95; // 95% 显著性
        } else if (difference > 0.05 && minSampleSize > 50) {
            return 0.90; // 90% 显著性
        } else {
            return 0.80; // 80% 显著性
        }
    }

    /**
     * 获取所有活跃测试
     */
    public List<ABTest> getActiveTests() {
        return activeTests.values().stream()
                .filter(test -> test.getStatus() == TestStatus.RUNNING)
                .collect(Collectors.toList());
    }

    /**
     * 删除测试
     */
    public void deleteTest(String testId) {
        ABTest test = activeTests.remove(testId);
        if (test != null) {
            for (TestVariant variant : test.getVariants()) {
                statistics.remove(testId + ":" + variant.getId());
            }
            log.info("Deleted A/B test: {}", testId);
        }
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ABTestConfig {
        private String name;
        private String description;
        private List<TestVariant> variants;
        private List<Double> trafficSplit; // 流量分配（总和为1.0）
        private List<String> metrics; // 评估指标
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ABTest {
        private String testId;
        private String name;
        private String description;
        private List<TestVariant> variants;
        private List<Double> trafficSplit;
        private List<String> metrics;
        private TestStatus status;
        private long startTime;
        private Long endTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestVariant {
        private String id;
        private String name;
        private String description;
        private Map<String, Object> configuration; // 变体的配置参数
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private long executionTime;
        private double accuracy;
        private double userSatisfaction;
        private Map<String, Object> customMetrics;
    }

    @Data
    public static class TestStatistics {
        private final String variantId;
        private final AtomicInteger totalExecutions = new AtomicInteger(0);
        private final AtomicInteger successfulExecutions = new AtomicInteger(0);
        private long totalExecutionTime = 0;
        private double totalAccuracy = 0.0;
        private double totalUserSatisfaction = 0.0;

        public TestStatistics(String variantId) {
            this.variantId = variantId;
        }

        public synchronized void recordResult(TestResult result) {
            totalExecutions.incrementAndGet();
            if (result.isSuccess()) {
                successfulExecutions.incrementAndGet();
            }
            totalExecutionTime += result.getExecutionTime();
            totalAccuracy += result.getAccuracy();
            totalUserSatisfaction += result.getUserSatisfaction();
        }

        public double getSuccessRate() {
            int total = totalExecutions.get();
            return total == 0 ? 0 : (double) successfulExecutions.get() / total;
        }

        public long getAverageExecutionTime() {
            int total = totalExecutions.get();
            return total == 0 ? 0 : totalExecutionTime / total;
        }

        public double getAverageAccuracy() {
            int total = totalExecutions.get();
            return total == 0 ? 0 : totalAccuracy / total;
        }

        public double getAverageUserSatisfaction() {
            int total = totalExecutions.get();
            return total == 0 ? 0 : totalUserSatisfaction / total;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantReport {
        private String variantId;
        private String variantName;
        private long totalExecutions;
        private double successRate;
        private long avgExecutionTime;
        private double avgAccuracy;
        private double avgUserSatisfaction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestReport {
        private String testId;
        private String testName;
        private TestStatus status;
        private List<VariantReport> variantReports;
        private String winner;
        private double statisticalSignificance;
        private long startTime;
        private Long endTime;
    }

    public enum TestStatus {
        DRAFT,      // 草稿
        RUNNING,    // 运行中
        STOPPED,    // 已停止
        COMPLETED   // 已完成
    }
}
