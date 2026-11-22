package com.easywing.platform.ai.debug.export;

import com.easywing.platform.ai.debug.AgentTrace;

/**
 * Agent 执行链路导出器。
 * <p>
 * 追踪进入终态（{@code completeTrace} / {@code failTrace}）时由 {@code DebugService} 调用一次，
 * 实现方可把链路数据推送到外部系统（Langfuse、OpenTelemetry 等）。
 * 导出器必须保证自身的失败不会向外抛出，避免影响主对话流程。
 */
public interface AiTraceExporter {

    /**
     * 导出一条追踪。
     *
     * @param trace 追踪数据，非空
     */
    void export(AgentTrace trace);
}
