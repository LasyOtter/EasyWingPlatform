package com.easywing.platform.ai.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceStoreTest {

    @Test
    void savesLoadsAndDeletesTrace() {
        InMemoryTraceStore store = new InMemoryTraceStore(100);
        store.save(trace("t1", 1L));

        assertThat(store.load("t1")).isPresent();
        assertThat(store.load("t1").orElseThrow().getTraceId()).isEqualTo("t1");
        assertThat(store.loadAll()).hasSize(1);

        store.delete("t1");
        assertThat(store.load("t1")).isEmpty();
    }

    @Test
    void ignoresTraceWithoutId() {
        InMemoryTraceStore store = new InMemoryTraceStore(100);

        store.save(AgentTrace.builder().agentId("a").build());

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void clearRemovesAllTraces() {
        InMemoryTraceStore store = new InMemoryTraceStore(100);
        store.save(trace("t1", 1L));
        store.save(trace("t2", 2L));

        store.clear();

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void trimsOldestTracesBeyondCapacity() {
        InMemoryTraceStore store = new InMemoryTraceStore(101);
        for (int i = 1; i <= 105; i++) {
            store.save(trace("t" + i, i));
        }

        assertThat(store.loadAll()).hasSize(101);
        assertThat(store.load("t1")).isEmpty();
        assertThat(store.load("t105")).isPresent();
    }

    private AgentTrace trace(String id, long startTime) {
        return AgentTrace.builder().traceId(id).startTime(startTime).build();
    }
}
