package com.easywing.platform.ai.debug;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agent 追踪内存存储
 * <p>
 * 当 {@code easywing.ai.trace.storage-type} 不是 {@code redis} 时生效。
 * 重启丢失，多实例各自独立，禁止用于生产。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.trace",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryTraceStore implements TraceStore {

    private final Map<String, AgentTrace> traces = new ConcurrentHashMap<>();
    private final int maxRecords;

    public InMemoryTraceStore(@Value("${easywing.ai.trace.max-records:1000}") int maxRecords) {
        this.maxRecords = Math.max(100, maxRecords);
        log.warn("AI Agent 追踪使用内存存储（easywing.ai.trace.storage-type=memory）：" + "重启即丢失，集群下各实例独立，禁止用于生产");
    }

    @Override
    public void save(AgentTrace trace) {
        if (trace == null || trace.getTraceId() == null) {
            return;
        }
        traces.put(trace.getTraceId(), trace);
        trim();
    }

    private void trim() {
        if (traces.size() <= maxRecords) {
            return;
        }
        List<String> expired = traces.values().stream()
                .sorted(Comparator.comparingLong(AgentTrace::getStartTime))
                .limit(traces.size() - maxRecords)
                .map(AgentTrace::getTraceId)
                .toList();
        expired.forEach(traces::remove);
        log.debug("Trace store trimmed {} oldest traces", expired.size());
    }

    @Override
    public Optional<AgentTrace> load(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    @Override
    public List<AgentTrace> loadAll() {
        return List.copyOf(traces.values());
    }

    @Override
    public void delete(String traceId) {
        traces.remove(traceId);
    }

    @Override
    public void clear() {
        traces.clear();
    }
}
