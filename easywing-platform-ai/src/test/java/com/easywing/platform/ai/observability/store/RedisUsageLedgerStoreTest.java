package com.easywing.platform.ai.observability.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.observability.UsageRecord;
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
class RedisUsageLedgerStoreTest {

    private static final String USAGE_KEY = "ai:usage";
    private static final String USAGE_TS_KEY = "ai:usage:ts";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisUsageLedgerStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        store = new RedisUsageLedgerStore(redisTemplate, objectMapper, 101);
    }

    @Test
    void saveWritesHashEntryAndTimestampIndex() {
        when(zSetOps.zCard(USAGE_TS_KEY)).thenReturn(1L);
        UsageRecord record = record("r1", "openai", "gpt-4o", 1234L);

        store.save(record);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(eq(USAGE_KEY), eq("r1"), valueCaptor.capture());
        assertThat(valueCaptor.getValue())
                .contains("\"userId\":\"alice\"")
                .contains("\"promptTokens\":10")
                .contains("\"estimatedCostUsd\":0.5");
        verify(zSetOps).add(USAGE_TS_KEY, "r1", 1234.0);
    }

    @Test
    void saveSkipsRecordWithoutId() {
        store.save(UsageRecord.builder().model("gpt-4o").build());

        verifyNoInteractions(hashOps, zSetOps);
    }

    @Test
    void loadAllDeserializesEntries() throws Exception {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("r1", objectMapper.writeValueAsString(record("r1", "openai", "gpt-4o", 1L)));
        entries.put("r2", objectMapper.writeValueAsString(record("r2", "deepseek", "chat", 2L)));
        when(hashOps.entries(USAGE_KEY)).thenReturn(entries);

        List<UsageRecord> records = store.loadAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(UsageRecord::getId).containsExactlyInAnyOrder("r1", "r2");
    }

    @Test
    void loadAllSkipsCorruptEntries() throws Exception {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("r1", objectMapper.writeValueAsString(record("r1", "openai", "gpt-4o", 1L)));
        entries.put("broken", "{not-json");
        when(hashOps.entries(USAGE_KEY)).thenReturn(entries);

        List<UsageRecord> records = store.loadAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getId()).isEqualTo("r1");
    }

    @Test
    void saveTrimsOldestRecordsBeyondCapacity() {
        when(zSetOps.zCard(USAGE_TS_KEY)).thenReturn(105L);
        when(zSetOps.range(USAGE_TS_KEY, 0, 3)).thenReturn(Set.of("r1", "r2", "r3", "r4"));

        store.save(record("r5", "openai", "gpt-4o", 5L));

        ArgumentCaptor<Object[]> hashKeys = ArgumentCaptor.forClass(Object[].class);
        verify(hashOps).delete(eq(USAGE_KEY), hashKeys.capture());
        assertThat(hashKeys.getValue()).containsExactlyInAnyOrder("r1", "r2", "r3", "r4");

        ArgumentCaptor<Object[]> zSetMembers = ArgumentCaptor.forClass(Object[].class);
        verify(zSetOps).remove(eq(USAGE_TS_KEY), zSetMembers.capture());
        assertThat(zSetMembers.getValue()).containsExactlyInAnyOrder("r1", "r2", "r3", "r4");
    }

    @Test
    void deleteRemovesRecordFromHashAndIndex() {
        store.delete("r1");

        verify(hashOps).delete(USAGE_KEY, "r1");
        verify(zSetOps).remove(USAGE_TS_KEY, "r1");
    }

    @Test
    void clearDeletesBothKeys() {
        store.clear();

        verify(redisTemplate).delete(USAGE_KEY);
        verify(redisTemplate).delete(USAGE_TS_KEY);
    }

    @Test
    void maxRecordsIsLowerBoundedAtOneHundred() {
        RedisUsageLedgerStore small = new RedisUsageLedgerStore(redisTemplate, objectMapper, 10);
        when(zSetOps.zCard(anyString())).thenReturn(50L);

        small.save(record("r1", "openai", "gpt-4o", 1L));

        // 50 <= 下限 100，不应触发淘汰
        verify(zSetOps, never()).range(anyString(), anyLong(), anyLong());
    }

    private UsageRecord record(String id, String provider, String model, long timestamp) {
        return UsageRecord.builder()
                .id(id)
                .userId("alice")
                .tenantId("t1")
                .provider(provider)
                .model(model)
                .promptTokens(10)
                .completionTokens(5)
                .totalTokens(15)
                .estimatedCostUsd(0.5)
                .timestamp(timestamp)
                .build();
    }
}
