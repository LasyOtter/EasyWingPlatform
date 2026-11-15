package com.easywing.platform.ai.debug;

import com.easywing.platform.ai.agent.AgentIteration;
import com.easywing.platform.ai.agent.AgentResponse;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行追踪信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTrace {

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * Agent ID
     */
    private String agentId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户输入
     */
    private String userInput;

    /**
     * 迭代列表
     */
    private List<AgentIteration> iterations;

    /**
     * 最终响应
     */
    private AgentResponse response;

    /**
     * 性能指标
     */
    private PerformanceMetrics metrics;

    /**
     * 工具调用统计
     */
    private Map<String, ToolCallStats> toolStats;

    /**
     * 时间线
     */
    private List<TimelineEvent> timeline;

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 结束时间
     */
    private long endTime;

    /**
     * 状态
     */
    private ExecutionStatus status;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行状态
     */
    public enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * 性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private long totalDurationMs;
        private long llmDurationMs;
        private long toolDurationMs;
        private int iterationCount;
        private int toolCallCount;
        private int tokenUsage;
    }

    /**
     * 工具调用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallStats {
        private String toolName;
        private int callCount;
        private int successCount;
        private int failureCount;
        private long totalDurationMs;
        private long avgDurationMs;
        private long minDurationMs;
        private long maxDurationMs;
    }

    /**
     * 时间线事件
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private long timestamp;
        private String type;
        private String description;
        private Object data;
    }
}
