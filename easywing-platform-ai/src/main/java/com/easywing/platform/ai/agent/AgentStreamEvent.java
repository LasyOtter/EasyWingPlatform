package com.easywing.platform.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 流式事件 - 用于 SSE 推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStreamEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 迭代序号
     */
    private int iterationNumber;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        START, // 开始执行
        THINKING, // 思考中
        TOOL_CALL_START, // 工具调用开始
        TOOL_CALL_END, // 工具调用结束
        OBSERVATION, // 观察结果
        ANSWER_CHUNK, // 答案片段（流式输出）
        FINAL_ANSWER, // 最终答案
        ERROR, // 错误
        COMPLETE // 完成
    }

    /**
     * 创建开始事件
     */
    public static AgentStreamEvent start() {
        return AgentStreamEvent.builder()
                .type(EventType.START)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建思考事件
     */
    public static AgentStreamEvent thinking(String thought, int iteration) {
        return AgentStreamEvent.builder()
                .type(EventType.THINKING)
                .data(thought)
                .iterationNumber(iteration)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建工具调用开始事件
     */
    public static AgentStreamEvent toolCallStart(String toolName, Object arguments, int iteration) {
        return AgentStreamEvent.builder()
                .type(EventType.TOOL_CALL_START)
                .data(new ToolCallData(toolName, arguments, null))
                .iterationNumber(iteration)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建工具调用结束事件
     */
    public static AgentStreamEvent toolCallEnd(String toolName, Object result, int iteration) {
        return AgentStreamEvent.builder()
                .type(EventType.TOOL_CALL_END)
                .data(new ToolCallData(toolName, null, result))
                .iterationNumber(iteration)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建观察事件
     */
    public static AgentStreamEvent observation(String observation, int iteration) {
        return AgentStreamEvent.builder()
                .type(EventType.OBSERVATION)
                .data(observation)
                .iterationNumber(iteration)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建答案片段事件
     */
    public static AgentStreamEvent answerChunk(String chunk) {
        return AgentStreamEvent.builder()
                .type(EventType.ANSWER_CHUNK)
                .data(chunk)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建最终答案事件
     */
    public static AgentStreamEvent finalAnswer(String answer) {
        return AgentStreamEvent.builder()
                .type(EventType.FINAL_ANSWER)
                .data(answer)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建错误事件
     */
    public static AgentStreamEvent error(String error) {
        return AgentStreamEvent.builder()
                .type(EventType.ERROR)
                .data(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建完成事件
     */
    public static AgentStreamEvent complete(AgentResponse response) {
        return AgentStreamEvent.builder()
                .type(EventType.COMPLETE)
                .data(response)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 工具调用数据
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCallData {
        private String toolName;
        private Object arguments;
        private Object result;
    }
}
