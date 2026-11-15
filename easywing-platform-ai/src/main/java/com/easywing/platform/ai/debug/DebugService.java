package com.easywing.platform.ai.debug;

import com.easywing.platform.ai.agent.AgentIteration;
import com.easywing.platform.ai.agent.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 调试服务 - 追踪和分析 Agent 执行
 */
@Slf4j
@Service
public class DebugService {

    private final Map<String, AgentTrace> traces = new ConcurrentHashMap<>();
    private final Map<String, List<AgentTrace.TimelineEvent>> timelines = new ConcurrentHashMap<>();

    /**
     * 开始追踪
     */
    public String startTrace(String agentId, String sessionId, String userInput) {
        String traceId = UUID.randomUUID().toString();

        AgentTrace trace = AgentTrace.builder()
                .traceId(traceId)
                .agentId(agentId)
                .sessionId(sessionId)
                .userInput(userInput)
                .iterations(new ArrayList<>())
                .timeline(new ArrayList<>())
                .startTime(System.currentTimeMillis())
                .status(AgentTrace.ExecutionStatus.RUNNING)
                .build();

        traces.put(traceId, trace);
        timelines.put(traceId, new ArrayList<>());

        addTimelineEvent(traceId, "START", "Agent execution started", null);
        log.info("Started trace: {}", traceId);

        return traceId;
    }

    /**
     * 记录迭代
     */
    public void recordIteration(String traceId, AgentIteration iteration) {
        AgentTrace trace = traces.get(traceId);
        if (trace != null) {
            trace.getIterations().add(iteration);

            addTimelineEvent(traceId, "ITERATION",
                    String.format("Iteration %d", iteration.getIterationNumber()),
                    iteration);
        }
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String traceId, String toolName, long durationMs, boolean success) {
        AgentTrace trace = traces.get(traceId);
        if (trace == null) return;

        addTimelineEvent(traceId, "TOOL_CALL",
                String.format("Tool: %s (%s)", toolName, success ? "success" : "failed"),
                Map.of("toolName", toolName, "duration", durationMs, "success", success));

        // 更新工具统计
        if (trace.getToolStats() == null) {
            trace.setToolStats(new HashMap<>());
        }

        trace.getToolStats().compute(toolName, (k, stats) -> {
            if (stats == null) {
                stats = AgentTrace.ToolCallStats.builder()
                        .toolName(toolName)
                        .callCount(0)
                        .successCount(0)
                        .failureCount(0)
                        .totalDurationMs(0)
                        .minDurationMs(Long.MAX_VALUE)
                        .maxDurationMs(0)
                        .build();
            }

            stats.setCallCount(stats.getCallCount() + 1);
            if (success) {
                stats.setSuccessCount(stats.getSuccessCount() + 1);
            } else {
                stats.setFailureCount(stats.getFailureCount() + 1);
            }

            stats.setTotalDurationMs(stats.getTotalDurationMs() + durationMs);
            stats.setMinDurationMs(Math.min(stats.getMinDurationMs(), durationMs));
            stats.setMaxDurationMs(Math.max(stats.getMaxDurationMs(), durationMs));
            stats.setAvgDurationMs(stats.getTotalDurationMs() / stats.getCallCount());

            return stats;
        });
    }

    /**
     * 完成追踪
     */
    public void completeTrace(String traceId, AgentResponse response) {
        AgentTrace trace = traces.get(traceId);
        if (trace == null) return;

        trace.setResponse(response);
        trace.setEndTime(System.currentTimeMillis());
        trace.setStatus(AgentTrace.ExecutionStatus.COMPLETED);

        // 计算性能指标
        AgentTrace.PerformanceMetrics metrics = calculateMetrics(trace);
        trace.setMetrics(metrics);

        addTimelineEvent(traceId, "COMPLETE", "Agent execution completed", null);
        log.info("Completed trace: {}, duration: {}ms", traceId, metrics.getTotalDurationMs());
    }

    /**
     * 标记追踪失败
     */
    public void failTrace(String traceId, String error) {
        AgentTrace trace = traces.get(traceId);
        if (trace == null) return;

        trace.setEndTime(System.currentTimeMillis());
        trace.setStatus(AgentTrace.ExecutionStatus.FAILED);
        trace.setError(error);

        addTimelineEvent(traceId, "ERROR", "Agent execution failed", Map.of("error", error));
        log.error("Failed trace: {}, error: {}", traceId, error);
    }

    /**
     * 获取追踪信息
     */
    public Optional<AgentTrace> getTrace(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    /**
     * 获取所有追踪
     */
    public List<AgentTrace> getAllTraces() {
        return new ArrayList<>(traces.values());
    }

    /**
     * 获取最近的追踪
     */
    public List<AgentTrace> getRecentTraces(int limit) {
        return traces.values().stream()
                .sorted(Comparator.comparingLong(AgentTrace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取调试报告
     */
    public DebugReport generateReport(String traceId) {
        AgentTrace trace = traces.get(traceId);
        if (trace == null) {
            return null;
        }

        return DebugReport.builder()
                .traceId(traceId)
                .summary(generateSummary(trace))
                .performanceAnalysis(analyzePerformance(trace))
                .iterationAnalysis(analyzeIterations(trace))
                .toolAnalysis(analyzeTools(trace))
                .recommendations(generateRecommendations(trace))
                .build();
    }

    /**
     * 清除追踪
     */
    public void clearTrace(String traceId) {
        traces.remove(traceId);
        timelines.remove(traceId);
        log.info("Cleared trace: {}", traceId);
    }

    /**
     * 清除所有追踪
     */
    public void clearAllTraces() {
        int count = traces.size();
        traces.clear();
        timelines.clear();
        log.info("Cleared {} traces", count);
    }

    /**
     * 添加时间线事件
     */
    private void addTimelineEvent(String traceId, String type, String description, Object data) {
        List<AgentTrace.TimelineEvent> timeline = timelines.get(traceId);
        if (timeline != null) {
            timeline.add(AgentTrace.TimelineEvent.builder()
                    .timestamp(System.currentTimeMillis())
                    .type(type)
                    .description(description)
                    .data(data)
                    .build());

            AgentTrace trace = traces.get(traceId);
            if (trace != null) {
                trace.setTimeline(timeline);
            }
        }
    }

    /**
     * 计算性能指标
     */
    private AgentTrace.PerformanceMetrics calculateMetrics(AgentTrace trace) {
        long totalDuration = trace.getEndTime() - trace.getStartTime();
        int iterationCount = trace.getIterations() != null ? trace.getIterations().size() : 0;

        long toolDuration = 0;
        int toolCallCount = 0;

        if (trace.getToolStats() != null) {
            for (AgentTrace.ToolCallStats stats : trace.getToolStats().values()) {
                toolDuration += stats.getTotalDurationMs();
                toolCallCount += stats.getCallCount();
            }
        }

        long llmDuration = totalDuration - toolDuration;

        return AgentTrace.PerformanceMetrics.builder()
                .totalDurationMs(totalDuration)
                .llmDurationMs(llmDuration)
                .toolDurationMs(toolDuration)
                .iterationCount(iterationCount)
                .toolCallCount(toolCallCount)
                .build();
    }

    /**
     * 生成摘要
     */
    private String generateSummary(AgentTrace trace) {
        return String.format(
                "Agent: %s, Status: %s, Duration: %dms, Iterations: %d, Tool Calls: %d",
                trace.getAgentId(),
                trace.getStatus(),
                trace.getEndTime() - trace.getStartTime(),
                trace.getIterations().size(),
                trace.getToolStats() != null ? trace.getToolStats().values().stream()
                        .mapToInt(AgentTrace.ToolCallStats::getCallCount).sum() : 0
        );
    }

    /**
     * 分析性能
     */
    private Map<String, Object> analyzePerformance(AgentTrace trace) {
        AgentTrace.PerformanceMetrics metrics = trace.getMetrics();
        Map<String, Object> analysis = new HashMap<>();

        analysis.put("总耗时", metrics.getTotalDurationMs() + "ms");
        analysis.put("LLM耗时", metrics.getLlmDurationMs() + "ms");
        analysis.put("工具耗时", metrics.getToolDurationMs() + "ms");
        analysis.put("LLM占比", String.format("%.1f%%",
                100.0 * metrics.getLlmDurationMs() / metrics.getTotalDurationMs()));
        analysis.put("工具占比", String.format("%.1f%%",
                100.0 * metrics.getToolDurationMs() / metrics.getTotalDurationMs()));

        return analysis;
    }

    /**
     * 分析迭代
     */
    private List<String> analyzeIterations(AgentTrace trace) {
        List<String> analysis = new ArrayList<>();

        for (AgentIteration iteration : trace.getIterations()) {
            analysis.add(String.format(
                    "迭代 %d: %s (耗时: %dms)",
                    iteration.getIterationNumber(),
                    iteration.getAction() != null ? iteration.getAction() : "思考",
                    iteration.getExecutionTimeMs()
            ));
        }

        return analysis;
    }

    /**
     * 分析工具
     */
    private Map<String, Object> analyzeTools(AgentTrace trace) {
        Map<String, Object> analysis = new HashMap<>();

        if (trace.getToolStats() != null) {
            trace.getToolStats().forEach((toolName, stats) -> {
                analysis.put(toolName, Map.of(
                        "调用次数", stats.getCallCount(),
                        "成功率", String.format("%.1f%%",
                                100.0 * stats.getSuccessCount() / stats.getCallCount()),
                        "平均耗时", stats.getAvgDurationMs() + "ms",
                        "最小耗时", stats.getMinDurationMs() + "ms",
                        "最大耗时", stats.getMaxDurationMs() + "ms"
                ));
            });
        }

        return analysis;
    }

    /**
     * 生成优化建议
     */
    private List<String> generateRecommendations(AgentTrace trace) {
        List<String> recommendations = new ArrayList<>();

        AgentTrace.PerformanceMetrics metrics = trace.getMetrics();

        // 检查迭代次数
        if (metrics.getIterationCount() > 5) {
            recommendations.add("迭代次数较多，考虑优化提示词或使用 Function Calling 模式");
        }

        // 检查工具耗时
        if (metrics.getToolDurationMs() > metrics.getLlmDurationMs() * 2) {
            recommendations.add("工具执行耗时较长，考虑启用并行执行或缓存");
        }

        // 检查工具失败率
        if (trace.getToolStats() != null) {
            trace.getToolStats().forEach((toolName, stats) -> {
                double failureRate = 100.0 * stats.getFailureCount() / stats.getCallCount();
                if (failureRate > 20) {
                    recommendations.add(String.format(
                            "工具 %s 失败率较高 (%.1f%%)，检查参数验证和错误处理",
                            toolName, failureRate));
                }
            });
        }

        if (recommendations.isEmpty()) {
            recommendations.add("执行正常，暂无优化建议");
        }

        return recommendations;
    }

    /**
     * 调试报告
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DebugReport {
        private String traceId;
        private String summary;
        private Map<String, Object> performanceAnalysis;
        private List<String> iterationAnalysis;
        private Map<String, Object> toolAnalysis;
        private List<String> recommendations;
    }
}
