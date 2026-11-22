package com.easywing.platform.ai.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RedisTraceStoreTest {

    private static final String TRACE_KEY = "ai:trace";
    private static final String TRACE_TS_KEY = "ai:trace:ts";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisTraceStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        store = new RedisTraceStore(redisTemplate, objectMapper, 101);
    }

    @Test
    void saveWritesHashEntryAndTimestampIndex() throws Exception {
        when(zSetOps.zCard(TRACE_TS_KEY)).thenReturn(1L);
        AgentTrace trace = trace("t1", "agent-1", 1234L);

        store.save(trace);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(eq(TRACE_KEY), eq("t1"), valueCaptor.capture());
        assertThat(valueCaptor.getValue()).contains("\"traceId\":\"t1\"").contains("\"agentId\":\"agent-1\"");
        verify(zSetOps).add(TRACE_TS_KEY, "t1", 1234.0);
    }

    @Test
    void saveSkipsTraceWithoutId() {
        store.save(AgentTrace.builder().agentId("agent-1").build());

        verifyNoInteractions(hashOps, zSetOps);
    }

    @Test
    void loadDeserializesTrace() throws Exception {
        when(hashOps.get(TRACE_KEY, "t1")).thenReturn(objectMapper.writeValueAsString(trace("t1", "agent-1", 1L)));

        AgentTrace loaded = store.load("t1").orElseThrow();

        assertThat(loaded.getTraceId()).isEqualTo("t1");
        assertThat(loaded.getAgentId()).isEqualTo("agent-1");
    }

    @Test
    void loadReturnsEmptyForMissingOrCorruptEntry() throws Exception {
        when(hashOps.get(TRACE_KEY, "missing")).thenReturn(null);
        assertThat(store.load("missing")).isEmpty();

        when(hashOps.get(TRACE_KEY, "broken")).thenReturn("{not-json");
        assertThat(store.load("broken")).isEmpty();
    }

    @Test
    void loadAllDeserializesEntries() throws Exception {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("t1", objectMapper.writeValueAsString(trace("t1", "agent-1", 1L)));
        entries.put("t2", objectMapper.writeValueAsString(trace("t2", "agent-2", 2L)));
        when(hashOps.entries(TRACE_KEY)).thenReturn(entries);

        List<AgentTrace> traces = store.loadAll();

        assertThat(traces).hasSize(2);
        assertThat(traces).extracting(AgentTrace::getTraceId).containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void saveTrimsOldestTracesBeyondCapacity() {
        when(zSetOps.zCard(TRACE_TS_KEY)).thenReturn(105L);
        when(zSetOps.range(TRACE_TS_KEY, 0, 3)).thenReturn(Set.of("t1", "t2", "t3", "t4"));

        store.save(trace("t5", "agent-1", 5L));

        ArgumentCaptor<Object[]> hashKeys = ArgumentCaptor.forClass(Object[].class);
        verify(hashOps).delete(eq(TRACE_KEY), hashKeys.capture());
        assertThat(hashKeys.getValue()).containsExactlyInAnyOrder("t1", "t2", "t3", "t4");

        ArgumentCaptor<Object[]> zSetMembers = ArgumentCaptor.forClass(Object[].class);
        verify(zSetOps).remove(eq(TRACE_TS_KEY), zSetMembers.capture());
        assertThat(zSetMembers.getValue()).containsExactlyInAnyOrder("t1", "t2", "t3", "t4");
    }

    @Test
    void maxRecordsIsLowerBoundedAtOneHundred() {
        RedisTraceStore small = new RedisTraceStore(redisTemplate, objectMapper, 10);
        when(zSetOps.zCard(anyString())).thenReturn(50L);

        small.save(trace("t1", "agent-1", 1L));

        verify(zSetOps, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    void deleteRemovesTraceFromHashAndIndex() {
        store.delete("t1");

        verify(hashOps).delete(TRACE_KEY, "t1");
        verify(zSetOps).remove(TRACE_TS_KEY, "t1");
    }

    @Test
    void clearDeletesBothKeys() {
        store.clear();

        verify(redisTemplate).delete(TRACE_KEY);
        verify(redisTemplate).delete(TRACE_TS_KEY);
    }

    private AgentTrace trace(String id, String agentId, long startTime) {
        return AgentTrace.builder()
                .traceId(id)
                .agentId(agentId)
                .startTime(startTime)
                .build();
    }
}
