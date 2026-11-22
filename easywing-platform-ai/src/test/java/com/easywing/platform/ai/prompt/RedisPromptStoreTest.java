package com.easywing.platform.ai.prompt;

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
 * {@link RedisPromptStore} 行为测试：Hash 字段读写、用户/租户过滤、排序与损坏条目容忍。
 * <p>
 * 用内存 Map 伪造 {@code StringRedisTemplate} 的 Hash 语义，不依赖真实 Redis。
 */
class RedisPromptStoreTest {

    private static final String KEY = "ai:prompts";

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisPromptStore store;

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

        store = new RedisPromptStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderId() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "标题A", 100L));

        Map<Object, Object> bucket = data.get(KEY);
        assertThat(bucket).containsKey("prompt-1");
        assertThat((String) bucket.get("prompt-1")).contains("\"title\":\"标题A\"");
    }

    @Test
    void saveIgnoresPromptWithoutId() {
        store.save(prompt(null, "user-1", "tenant-a", "标题A", 100L));
        store.save(null);

        assertThat(data.getOrDefault(KEY, Map.of())).isEmpty();
    }

    @Test
    void saveOverwritesSameId() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "旧", 100L));
        store.save(prompt("prompt-1", "user-1", "tenant-a", "新", 200L));

        assertThat(store.listByUser("user-1", "tenant-a")).hasSize(1);
        assertThat(store.listByUser("user-1", "tenant-a").get(0).getTitle()).isEqualTo("新");
    }

    @Test
    void findByIdReturnsSavedPrompt() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "标题A", 100L));

        PromptDefinition loaded = store.findById("prompt-1");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo("prompt-1");
        assertThat(loaded.getContent()).isEqualTo("正文 {{变量}}");
        assertThat(loaded.getVariables()).containsExactly("变量");
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        assertThat(store.findById("missing")).isNull();
    }

    @Test
    void findByIdReturnsNullOnCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("prompt-1", "{not-json");

        assertThat(store.findById("prompt-1")).isNull();
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "A", 100L));
        store.save(prompt("prompt-2", "user-1", "tenant-a", "B", 200L));

        store.delete("prompt-1");

        assertThat(store.listByUser("user-1", "tenant-a"))
                .extracting(PromptDefinition::getId)
                .containsExactly("prompt-2");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");

        assertThat(data).isEmpty();
    }

    @Test
    void listByUserFiltersByUserAndTenant() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "A", 100L));
        store.save(prompt("prompt-2", "user-2", "tenant-a", "B", 200L));
        store.save(prompt("prompt-3", "user-1", "tenant-b", "C", 300L));

        List<PromptDefinition> prompts = store.listByUser("user-1", "tenant-a");

        assertThat(prompts).extracting(PromptDefinition::getId).containsExactly("prompt-1");
    }

    @Test
    void listByUserSortsByUpdatedAtDesc() {
        store.save(prompt("prompt-1", "user-1", "tenant-a", "A", 100L));
        store.save(prompt("prompt-2", "user-1", "tenant-a", "B", 300L));
        store.save(prompt("prompt-3", "user-1", "tenant-a", "C", 200L));

        assertThat(store.listByUser("user-1", "tenant-a"))
                .extracting(PromptDefinition::getId)
                .containsExactly("prompt-2", "prompt-3", "prompt-1");
    }

    @Test
    void listByUserTreatsBlankTenantAsDefault() {
        store.save(prompt("prompt-1", "user-1", null, "A", 100L));
        store.save(prompt("prompt-2", "user-1", "tenant-a", "B", 200L));

        assertThat(store.listByUser("user-1", null))
                .extracting(PromptDefinition::getId)
                .containsExactly("prompt-1");
    }

    @Test
    void listByUserSkipsCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("prompt-bad", "{not-json");
        store.save(prompt("prompt-good", "user-1", "tenant-a", "A", 100L));

        assertThat(store.listByUser("user-1", "tenant-a"))
                .extracting(PromptDefinition::getId)
                .containsExactly("prompt-good");
    }

    private PromptDefinition prompt(String id, String userId, String tenantId, String title, long updatedAt) {
        return PromptDefinition.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .title(title)
                .content("正文 {{变量}}")
                .category("default")
                .variables(List.of("变量"))
                .createdAt(updatedAt - 10L)
                .updatedAt(updatedAt)
                .build();
    }
}
