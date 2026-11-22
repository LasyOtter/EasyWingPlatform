package com.easywing.platform.ai.agentdef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link RedisAgentDefinitionStore} 行为测试：Hash 字段读写、缺失/损坏条目容忍、排序稳定性。
 * <p>
 * 用内存 Map 伪造 {@code StringRedisTemplate} 的 Hash 语义，不依赖真实 Redis，
 * 保证「多实例共享同一份数据」的存储契约可在 CI 中验证。
 */
class RedisAgentDefinitionStoreTest {

    private static final String KEY = "ai:agent-defs";

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisAgentDefinitionStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        doAnswer(invocation -> {
                    data.computeIfAbsent(invocation.getArgument(0), key -> new HashMap<>())
                            .put(invocation.getArgument(1), invocation.getArgument(2));
                    return null;
                })
                .when(hashOps)
                .put(anyString(), any(), any());
        when(hashOps.get(anyString(), any())).thenAnswer(invocation -> {
            Map<Object, Object> bucket = data.get(invocation.getArgument(0));
            return bucket == null ? null : bucket.get(invocation.getArgument(1));
        });
        when(hashOps.entries(anyString())).thenAnswer(invocation -> {
            Map<Object, Object> bucket = data.get(invocation.getArgument(0));
            return bucket == null ? new HashMap<>() : new HashMap<>(bucket);
        });
        doAnswer(invocation -> {
                    Map<Object, Object> bucket = data.get(invocation.getArgument(0));
                    if (bucket != null) {
                        bucket.remove(invocation.getArgument(1));
                    }
                    return null;
                })
                .when(hashOps)
                .delete(anyString(), any());

        store = new RedisAgentDefinitionStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderDefinitionId() {
        store.save(definition("def-1", "alpha", 100L));

        Map<Object, Object> bucket = data.get(KEY);
        assertThat(bucket).containsKey("def-1");
        assertThat(bucket.get("def-1")).isInstanceOf(String.class);
        assertThat((String) bucket.get("def-1")).contains("\"name\":\"alpha\"");
    }

    @Test
    void saveIgnoresDefinitionWithoutId() {
        store.save(definition(null, "alpha", 100L));
        store.save(null);

        assertThat(data.getOrDefault(KEY, Map.of())).isEmpty();
    }

    @Test
    void saveOverwritesSameId() {
        store.save(definition("def-1", "旧", 100L));
        store.save(definition("def-1", "新", 200L));

        assertThat(store.listAll()).hasSize(1);
        assertThat(store.listAll().get(0).getName()).isEqualTo("新");
    }

    @Test
    void findByIdReturnsSavedDefinition() {
        store.save(definition("def-1", "alpha", 100L));

        AgentDefinition loaded = store.findById("def-1");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo("def-1");
        assertThat(loaded.getName()).isEqualTo("alpha");
        assertThat(loaded.getUpdatedAt()).isEqualTo(100L);
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        assertThat(store.findById("missing")).isNull();
    }

    @Test
    void findByIdReturnsNullOnCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("def-1", "{not-json");

        assertThat(store.findById("def-1")).isNull();
    }

    @Test
    void listAllReturnsEmptyWhenNothingStored() {
        assertThat(store.listAll()).isEmpty();
    }

    @Test
    void listAllDeserializesEveryEntry() {
        store.save(definition("def-1", "alpha", 100L));
        store.save(definition("def-2", "beta", 200L));

        List<AgentDefinition> loaded = store.listAll();

        assertThat(loaded).extracting(AgentDefinition::getId).containsExactlyInAnyOrder("def-1", "def-2");
        assertThat(loaded).extracting(AgentDefinition::getTenantId).contains("tenant-a");
    }

    @Test
    void listAllSkipsCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("def-bad", "{not-json");
        data.get(KEY).put("def-good", serialized("def-good", "beta", 200L));

        List<AgentDefinition> loaded = store.listAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getId()).isEqualTo("def-good");
    }

    @Test
    void listAllSortsByUpdatedAtThenIdDescending() {
        store.save(definition("def-old", "old", 100L));
        store.save(definition("def-new", "new", 200L));

        assertThat(store.listAll()).extracting(AgentDefinition::getId).containsExactly("def-new", "def-old");
    }

    @Test
    void listAllBreaksUpdatedAtTieByIdDescending() {
        store.save(definition("def-a", "a", 100L));
        store.save(definition("def-b", "b", 100L));

        assertThat(store.listAll()).extracting(AgentDefinition::getId).containsExactly("def-b", "def-a");
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save(definition("def-1", "alpha", 100L));
        store.save(definition("def-2", "beta", 200L));

        store.delete("def-1");

        assertThat(store.listAll()).extracting(AgentDefinition::getId).containsExactly("def-2");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");

        assertThat(data).isEmpty();
    }

    private AgentDefinition definition(String id, String name, long updatedAt) {
        return AgentDefinition.builder()
                .id(id)
                .name(name)
                .description("描述-" + name)
                .systemPrompt("你是" + name)
                .model("gpt-4o")
                .tools(List.of("calculator"))
                .skills(List.of("data-analysis"))
                .visibility("PRIVATE")
                .ownerId("user-1")
                .tenantId("tenant-a")
                .sharedWithUsers(List.of("user-2"))
                .sharedWithGroups(List.of("group-1"))
                .createdAt(updatedAt - 10L)
                .updatedAt(updatedAt)
                .build();
    }

    private String serialized(String id, String name, long updatedAt) {
        try {
            return new ObjectMapper().writeValueAsString(definition(id, name, updatedAt));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
