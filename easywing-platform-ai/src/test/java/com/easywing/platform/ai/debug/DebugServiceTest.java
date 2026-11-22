package com.easywing.platform.ai.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.agent.AgentIteration;
import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.debug.export.AiTraceExporter;
import com.easywing.platform.ai.debug.export.NoOpAiTraceExporter;
import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DebugServiceTest {

    private final InMemoryTraceStore store = new InMemoryTraceStore(100);
    private final DebugService service = new DebugService(store, new NoOpAiTraceExporter());

    @Test
    void startTracePersistsRunningTraceWithTimeline() {
        String traceId = service.startTrace("agent-1", "session-1", "hello");

        Optional<AgentTrace> trace = service.getTrace(traceId);
        assertThat(trace).isPresent();
        assertThat(trace.get().getStatus()).isEqualTo(AgentTrace.ExecutionStatus.RUNNING);
        assertThat(trace.get().getAgentId()).isEqualTo("agent-1");
        assertThat(trace.get().getSessionId()).isEqualTo("session-1");
        assertThat(trace.get().getUserInput()).isEqualTo("hello");
        assertThat(trace.get().getIterations()).isEmpty();
        assertThat(trace.get().getTimeline())
                .extracting(AgentTrace.TimelineEvent::getType)
                .contains("START");
    }

    @Test
    void recordIterationAndToolCallUpdateTrace() {
        String traceId = service.startTrace("agent-1", "session-1", "hello");

        service.recordIteration(
                traceId,
                AgentIteration.builder()
                        .iterationNumber(1)
                        .thought("need weather")
                        .action("get_weather")
                        .finalAnswer(false)
                        .executionTimeMs(5)
                        .build());
        service.recordToolCall(traceId, "get_weather", 12L, true);

        AgentTrace trace = service.getTrace(traceId).orElseThrow();
        assertThat(trace.getIterations()).hasSize(1);
        assertThat(trace.getToolStats()).containsKey("get_weather");
        assertThat(trace.getToolStats().get("get_weather").getCallCount()).isEqualTo(1);
        assertThat(trace.getToolStats().get("get_weather").getSuccessCount()).isEqualTo(1);
        assertThat(trace.getTimeline())
                .extracting(AgentTrace.TimelineEvent::getType)
                .contains("ITERATION", "TOOL_CALL");
    }

    @Test
    void completeTraceComputesMetricsAndPersists() {
        String traceId = service.startTrace("agent-1", "session-1", "hello");
        service.completeTrace(
                traceId,
                AgentResponse.builder()
                        .agentId("agent-1")
                        .sessionId("session-1")
                        .completed(true)
                        .message(ChatMessage.builder()
                                .role("assistant")
                                .content("hi")
                                .build())
                        .build());

        AgentTrace trace = service.getTrace(traceId).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo(AgentTrace.ExecutionStatus.COMPLETED);
        assertThat(trace.getEndTime()).isPositive();
        assertThat(trace.getMetrics()).isNotNull();
        assertThat(trace.getMetrics().getTotalDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(trace.getTimeline())
                .extracting(AgentTrace.TimelineEvent::getType)
                .contains("COMPLETE");
    }

    @Test
    void failTraceMarksFailed() {
        String traceId = service.startTrace("agent-1", "session-1", "hello");

        service.failTrace(traceId, "boom");

        AgentTrace trace = service.getTrace(traceId).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo(AgentTrace.ExecutionStatus.FAILED);
        assertThat(trace.getError()).isEqualTo("boom");
        assertThat(trace.getTimeline())
                .extracting(AgentTrace.TimelineEvent::getType)
                .contains("ERROR");
    }

    @Test
    void recentTracesSortedByStartTimeDescending() {
        // 直接写入不同 startTime 的追踪，避免同一毫秒内 startTrace 的排序不确定
        store.save(AgentTrace.builder().traceId("t-old").startTime(1L).build());
        store.save(AgentTrace.builder().traceId("t-new").startTime(2L).build());

        assertThat(service.getRecentTraces(1))
                .extracting(AgentTrace::getTraceId)
                .containsExactly("t-new");
        assertThat(service.getRecentTraces(10))
                .extracting(AgentTrace::getTraceId)
                .containsExactly("t-new", "t-old");
    }

    @Test
    void generateReportReturnsAnalysis() {
        String traceId = service.startTrace("agent-1", "session-1", "hello");
        service.recordToolCall(traceId, "get_weather", 10L, true);
        service.completeTrace(
                traceId,
                AgentResponse.builder()
                        .agentId("agent-1")
                        .sessionId("session-1")
                        .completed(true)
                        .build());

        DebugService.DebugReport report = service.generateReport(traceId);

        assertThat(report).isNotNull();
        assertThat(report.getTraceId()).isEqualTo(traceId);
        assertThat(report.getSummary()).contains("agent-1");
        assertThat(report.getToolAnalysis()).containsKey("get_weather");
        assertThat(service.generateReport("missing")).isNull();
    }

    @Test
    void tracesAreSharedAcrossServiceInstances() {
        InMemoryTraceStore shared = new InMemoryTraceStore(100);
        DebugService instanceA = new DebugService(shared, new NoOpAiTraceExporter());
        DebugService instanceB = new DebugService(shared, new NoOpAiTraceExporter());

        String traceId = instanceA.startTrace("agent-1", "session-1", "hello");
        instanceA.recordToolCall(traceId, "calculator", 7L, true);

        AgentTrace seenByB = instanceB.getTrace(traceId).orElseThrow();
        assertThat(seenByB.getUserInput()).isEqualTo("hello");
        assertThat(seenByB.getToolStats()).containsKey("calculator");

        instanceB.clearTrace(traceId);
        assertThat(instanceA.getTrace(traceId)).isEmpty();
    }

    @Test
    void clearAllTracesRemovesEverything() {
        service.startTrace("agent-1", "session-1", "a");
        service.startTrace("agent-1", "session-1", "b");

        service.clearAllTraces();

        assertThat(service.getAllTraces()).isEmpty();
    }

    @Test
    void completedTraceIsForwardedToExporter() {
        RecordingExporter exporter = new RecordingExporter();
        DebugService exporting = new DebugService(store, exporter);

        String traceId = exporting.startTrace("agent-1", "session-1", "hello");
        exporting.completeTrace(traceId, AgentResponse.builder().build());

        assertThat(exporter.exported).containsExactly(traceId);
    }

    @Test
    void failedTraceIsForwardedToExporter() {
        RecordingExporter exporter = new RecordingExporter();
        DebugService exporting = new DebugService(store, exporter);

        String traceId = exporting.startTrace("agent-1", "session-1", "hello");
        exporting.failTrace(traceId, "boom");

        assertThat(exporter.exported).containsExactly(traceId);
    }

    @Test
    void exporterFailureDoesNotBreakTraceCompletion() {
        DebugService exporting = new DebugService(store, trace -> {
            throw new IllegalStateException("exporter down");
        });

        String traceId = exporting.startTrace("agent-1", "session-1", "hello");
        exporting.completeTrace(traceId, AgentResponse.builder().build());

        assertThat(exporting.getTrace(traceId)).isPresent();
        assertThat(exporting.getTrace(traceId).orElseThrow().getStatus())
                .isEqualTo(AgentTrace.ExecutionStatus.COMPLETED);
    }

    private static final class RecordingExporter implements AiTraceExporter {

        private final List<String> exported = new ArrayList<>();

        @Override
        public void export(AgentTrace trace) {
            exported.add(trace.getTraceId());
        }
    }
}
