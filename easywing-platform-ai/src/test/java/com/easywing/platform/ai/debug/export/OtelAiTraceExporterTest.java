package com.easywing.platform.ai.debug.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.easywing.platform.ai.debug.AgentTrace;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class OtelAiTraceExporterTest {

    @Test
    void emitsSpanWithAgentAttributes() {
        CapturingExporter capturing = new CapturingExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(capturing))
                .build();
        try {
            new OtelAiTraceExporter(provider.get("test")).export(completedTrace());

            assertThat(capturing.spans).hasSize(1);
            SpanData span = capturing.spans.getFirst();
            assertThat(span.getName()).isEqualTo("easywing.ai.agent");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("easywing.ai.agent.id")))
                    .isEqualTo("agent-1");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("easywing.ai.session.id")))
                    .isEqualTo("session-1");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("easywing.ai.trace.status")))
                    .isEqualTo("COMPLETED");
            assertThat(span.getAttributes().get(AttributeKey.longKey("easywing.ai.trace.duration_ms")))
                    .isEqualTo(120L);
            assertThat(span.getAttributes().get(AttributeKey.longKey("easywing.ai.trace.tokens")))
                    .isEqualTo(34L);
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void failedTraceMarksSpanError() {
        CapturingExporter capturing = new CapturingExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(capturing))
                .build();
        try {
            AgentTrace failed = AgentTrace.builder()
                    .traceId("trace-2")
                    .agentId("agent-1")
                    .sessionId("session-1")
                    .status(AgentTrace.ExecutionStatus.FAILED)
                    .error("provider timeout")
                    .build();

            new OtelAiTraceExporter(provider.get("test")).export(failed);

            assertThat(capturing.spans).hasSize(1);
            SpanData span = capturing.spans.getFirst();
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("easywing.ai.trace.error")))
                    .isEqualTo("provider timeout");
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void blankTraceIdIsSkipped() {
        CapturingExporter capturing = new CapturingExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(capturing))
                .build();
        try {
            assertThatCode(() -> new OtelAiTraceExporter(provider.get("test"))
                            .export(AgentTrace.builder().agentId("agent-1").build()))
                    .doesNotThrowAnyException();
            assertThat(capturing.spans).isEmpty();
        } finally {
            provider.shutdown();
        }
    }

    private AgentTrace completedTrace() {
        return AgentTrace.builder()
                .traceId("trace-1")
                .agentId("agent-1")
                .sessionId("session-1")
                .userInput("hi")
                .status(AgentTrace.ExecutionStatus.COMPLETED)
                .metrics(AgentTrace.PerformanceMetrics.builder()
                        .totalDurationMs(120L)
                        .tokenUsage(34)
                        .iterationCount(2)
                        .toolCallCount(1)
                        .build())
                .build();
    }

    private static final class CapturingExporter implements SpanExporter {

        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
