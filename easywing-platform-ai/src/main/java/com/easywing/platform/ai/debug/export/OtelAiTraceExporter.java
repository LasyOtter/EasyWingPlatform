package com.easywing.platform.ai.debug.export;

import com.easywing.platform.ai.debug.AgentTrace;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenTelemetry 链路导出器：把一次 Agent 执行写成一条 OTel span。
 * <p>
 * 依赖服务引入 {@code easywing-observability}（提供 {@link Tracer}）。
 * 需同时开启 {@code easywing.otel.enabled=true}（默认开启）与
 * {@code easywing.ai.trace.exporter=otel}，否则容器中没有可用的 {@code Tracer} Bean。
 * span 名称固定为 {@code easywing.ai.agent}，属性携带 agentId / sessionId / 状态 / 耗时 / token。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.trace", name = "exporter", havingValue = "otel")
public class OtelAiTraceExporter implements AiTraceExporter {

    private static final String SPAN_NAME = "easywing.ai.agent";
    private static final int MAX_ERROR_LENGTH = 1000;

    private static final AttributeKey<String> AGENT_ID = AttributeKey.stringKey("easywing.ai.agent.id");
    private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("easywing.ai.session.id");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("easywing.ai.trace.status");
    private static final AttributeKey<String> ERROR = AttributeKey.stringKey("easywing.ai.trace.error");
    private static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("easywing.ai.trace.duration_ms");
    private static final AttributeKey<Long> TOKEN_USAGE = AttributeKey.longKey("easywing.ai.trace.tokens");
    private static final AttributeKey<Long> ITERATION_COUNT = AttributeKey.longKey("easywing.ai.trace.iterations");
    private static final AttributeKey<Long> TOOL_CALL_COUNT = AttributeKey.longKey("easywing.ai.trace.tool_calls");

    private final Tracer tracer;

    public OtelAiTraceExporter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void export(AgentTrace trace) {
        if (trace == null || trace.getTraceId() == null || trace.getTraceId().isBlank()) {
            return;
        }
        try {
            AgentTrace.PerformanceMetrics metrics = trace.getMetrics();
            Span span = tracer.spanBuilder(SPAN_NAME)
                    .setAttribute(AGENT_ID, nullToEmpty(trace.getAgentId()))
                    .setAttribute(SESSION_ID, nullToEmpty(trace.getSessionId()))
                    .setAttribute(
                            STATUS,
                            trace.getStatus() != null ? trace.getStatus().name() : "UNKNOWN")
                    .setAttribute(DURATION_MS, metrics != null ? metrics.getTotalDurationMs() : 0L)
                    .setAttribute(TOKEN_USAGE, metrics != null ? metrics.getTokenUsage() : 0L)
                    .setAttribute(ITERATION_COUNT, metrics != null ? metrics.getIterationCount() : 0L)
                    .setAttribute(TOOL_CALL_COUNT, metrics != null ? metrics.getToolCallCount() : 0L)
                    .startSpan();

            if (trace.getError() != null && !trace.getError().isBlank()) {
                span.setAttribute(ERROR, truncate(trace.getError()));
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        } catch (Exception ex) {
            log.warn("OpenTelemetry 链路导出异常（已忽略）：traceId={}, reason={}", trace.getTraceId(), ex.getMessage());
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
