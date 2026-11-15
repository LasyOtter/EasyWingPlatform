package com.easywing.platform.ai.monitoring;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 监控仪表板服务 - 实时监控系统状态和性能
 */
@Slf4j
@Service
public class MonitoringDashboard {

    private final Map<String, AgentMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final Map<String, ToolMetrics> toolMetrics = new ConcurrentHashMap<>();
    private final List<SystemEvent> recentEvents = Collections.synchronizedList(new ArrayList<>());
    private final SystemMetrics systemMetrics = new SystemMetrics();

    private static final int MAX_RECENT_EVENTS = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 记录 Agent 执行
     */
    public void recordAgentExecution(String agentId, long executionTime, boolean success, int iterationCount) {

        AgentMetrics metrics = agentMetrics.computeIfAbsent(agentId, k -> new AgentMetrics(agentId));

        metrics.recordExecution(executionTime, success, iterationCount);
        systemMetrics.recordExecution(executionTime, success);

        // 记录事件
        addEvent(SystemEvent.builder()
                .type("AGENT_EXECUTION")
                .agentId(agentId)
                .success(success)
                .duration(executionTime)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String toolName, long executionTime, boolean success) {

        ToolMetrics metrics = toolMetrics.computeIfAbsent(toolName, k -> new ToolMetrics(toolName));

        metrics.recordCall(executionTime, success);

        addEvent(SystemEvent.builder()
                .type("TOOL_CALL")
                .toolName(toolName)
                .success(success)
                .duration(executionTime)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 获取实时仪表板数据
     */
    public DashboardData getDashboardData() {
        return DashboardData.builder()
                .systemMetrics(systemMetrics.snapshot())
                .agentMetrics(getTopAgents(10))
                .toolMetrics(getTopTools(10))
                .recentEvents(getRecentEvents(20))
                .alerts(getActiveAlerts())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 获取系统概览
     */
    public SystemOverview getSystemOverview() {
        long totalExecutions = systemMetrics.getTotalExecutions().get();
        long totalToolCalls = toolMetrics.values().stream()
                .mapToLong(m -> m.getTotalCalls().get())
                .sum();

        return SystemOverview.builder()
                .totalExecutions(totalExecutions)
                .totalToolCalls(totalToolCalls)
                .successRate(systemMetrics.getSuccessRate())
                .averageExecutionTime(systemMetrics.getAverageExecutionTime())
                .activeAgents(agentMetrics.size())
                .availableTools(toolMetrics.size())
                .uptime(calculateUptime())
                .build();
    }

    /**
     * 获取性能统计
     */
    public PerformanceStats getPerformanceStats(String period) {
        // 简化实现：返回当前统计
        return PerformanceStats.builder()
                .period(period)
                .totalRequests(systemMetrics.getTotalExecutions().get())
                .successfulRequests(systemMetrics.getSuccessfulExecutions().get())
                .failedRequests(systemMetrics.getFailedExecutions().get())
                .averageResponseTime(systemMetrics.getAverageExecutionTime())
                .p50ResponseTime(systemMetrics.getAverageExecutionTime())
                .p95ResponseTime((long) (systemMetrics.getAverageExecutionTime() * 1.5))
                .p99ResponseTime((long) (systemMetrics.getAverageExecutionTime() * 2))
                .requestsPerSecond(calculateRequestsPerSecond())
                .build();
    }

    /**
     * 获取热门 Agent
     */
    private List<AgentMetrics> getTopAgents(int limit) {
        return agentMetrics.values().stream()
                .sorted(Comparator.comparingLong(
                                (AgentMetrics a) -> a.getTotalExecutions().get())
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门工具
     */
    private List<ToolMetrics> getTopTools(int limit) {
        return toolMetrics.values().stream()
                .sorted(Comparator.comparingLong(
                                (ToolMetrics t) -> t.getTotalCalls().get())
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取最近事件
     */
    private List<SystemEvent> getRecentEvents(int limit) {
        synchronized (recentEvents) {
            return recentEvents.stream()
                    .sorted(Comparator.comparingLong(SystemEvent::getTimestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 获取活跃告警
     */
    private List<Alert> getActiveAlerts() {
        List<Alert> alerts = new ArrayList<>();

        // 检查成功率
        if (systemMetrics.getSuccessRate() < 0.8) {
            alerts.add(Alert.builder()
                    .level(AlertLevel.WARNING)
                    .message("系统成功率低于 80%")
                    .metric("success_rate")
                    .currentValue(systemMetrics.getSuccessRate())
                    .threshold(0.8)
                    .build());
        }

        // 检查响应时间
        if (systemMetrics.getAverageExecutionTime() > 10000) {
            alerts.add(Alert.builder()
                    .level(AlertLevel.WARNING)
                    .message("平均响应时间超过 10 秒")
                    .metric("response_time")
                    .currentValue((double) systemMetrics.getAverageExecutionTime())
                    .threshold(10000.0)
                    .build());
        }

        return alerts;
    }

    /**
     * 添加事件
     */
    private void addEvent(SystemEvent event) {
        synchronized (recentEvents) {
            recentEvents.add(event);
            if (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.remove(0);
            }
        }
    }

    /**
     * 计算运行时间
     */
    private long calculateUptime() {
        // 简化实现：返回固定值
        return System.currentTimeMillis() - systemMetrics.getStartTime();
    }

    /**
     * 计算每秒请求数
     */
    private double calculateRequestsPerSecond() {
        long uptime = calculateUptime();
        if (uptime == 0) return 0;
        return (double) systemMetrics.getTotalExecutions().get() / (uptime / 1000.0);
    }

    /**
     * 重置统计
     */
    public void resetStatistics() {
        agentMetrics.clear();
        toolMetrics.clear();
        recentEvents.clear();
        systemMetrics.reset();
        log.info("Statistics reset");
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardData {
        private SystemMetrics.Snapshot systemMetrics;
        private List<AgentMetrics> agentMetrics;
        private List<ToolMetrics> toolMetrics;
        private List<SystemEvent> recentEvents;
        private List<Alert> alerts;
        private long timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemOverview {
        private long totalExecutions;
        private long totalToolCalls;
        private double successRate;
        private long averageExecutionTime;
        private int activeAgents;
        private int availableTools;
        private long uptime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceStats {
        private String period;
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private long averageResponseTime;
        private long p50ResponseTime;
        private long p95ResponseTime;
        private long p99ResponseTime;
        private double requestsPerSecond;
    }

    @Data
    public static class AgentMetrics {
        private final String agentId;
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong successfulExecutions = new AtomicLong(0);
        private final AtomicLong failedExecutions = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicInteger totalIterations = new AtomicInteger(0);

        public AgentMetrics(String agentId) {
            this.agentId = agentId;
        }

        public void recordExecution(long executionTime, boolean success, int iterations) {
            totalExecutions.incrementAndGet();
            if (success) {
                successfulExecutions.incrementAndGet();
            } else {
                failedExecutions.incrementAndGet();
            }
            totalExecutionTime.addAndGet(executionTime);
            totalIterations.addAndGet(iterations);
        }

        public double getSuccessRate() {
            long total = totalExecutions.get();
            return total == 0 ? 0 : (double) successfulExecutions.get() / total;
        }

        public long getAverageExecutionTime() {
            long total = totalExecutions.get();
            return total == 0 ? 0 : totalExecutionTime.get() / total;
        }

        public double getAverageIterations() {
            long total = totalExecutions.get();
            return total == 0 ? 0 : (double) totalIterations.get() / total;
        }
    }

    @Data
    public static class ToolMetrics {
        private final String toolName;
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successfulCalls = new AtomicLong(0);
        private final AtomicLong failedCalls = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);

        public ToolMetrics(String toolName) {
            this.toolName = toolName;
        }

        public void recordCall(long executionTime, boolean success) {
            totalCalls.incrementAndGet();
            if (success) {
                successfulCalls.incrementAndGet();
            } else {
                failedCalls.incrementAndGet();
            }
            totalExecutionTime.addAndGet(executionTime);
        }

        public double getSuccessRate() {
            long total = totalCalls.get();
            return total == 0 ? 0 : (double) successfulCalls.get() / total;
        }

        public long getAverageExecutionTime() {
            long total = totalCalls.get();
            return total == 0 ? 0 : totalExecutionTime.get() / total;
        }
    }

    @Data
    public static class SystemMetrics {
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong successfulExecutions = new AtomicLong(0);
        private final AtomicLong failedExecutions = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final long startTime = System.currentTimeMillis();

        public void recordExecution(long executionTime, boolean success) {
            totalExecutions.incrementAndGet();
            if (success) {
                successfulExecutions.incrementAndGet();
            } else {
                failedExecutions.incrementAndGet();
            }
            totalExecutionTime.addAndGet(executionTime);
        }

        public double getSuccessRate() {
            long total = totalExecutions.get();
            return total == 0 ? 0 : (double) successfulExecutions.get() / total;
        }

        public long getAverageExecutionTime() {
            long total = totalExecutions.get();
            return total == 0 ? 0 : totalExecutionTime.get() / total;
        }

        public void reset() {
            totalExecutions.set(0);
            successfulExecutions.set(0);
            failedExecutions.set(0);
            totalExecutionTime.set(0);
        }

        public Snapshot snapshot() {
            return new Snapshot(
                    totalExecutions.get(),
                    successfulExecutions.get(),
                    failedExecutions.get(),
                    getSuccessRate(),
                    getAverageExecutionTime());
        }

        @Data
        @AllArgsConstructor
        public static class Snapshot {
            private long totalExecutions;
            private long successfulExecutions;
            private long failedExecutions;
            private double successRate;
            private long averageExecutionTime;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemEvent {
        private String type;
        private String agentId;
        private String toolName;
        private boolean success;
        private long duration;
        private long timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private AlertLevel level;
        private String message;
        private String metric;
        private double currentValue;
        private double threshold;
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
