package com.easywing.platform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.config.AiProperties.CustomEndpointConfig;
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
 * {@link RedisCustomEndpointStore} 行为测试：Hash 字段读写、缺失/损坏条目容忍。
 * <p>
 * 用内存 Map 伪造 {@code StringRedisTemplate} 的 Hash 语义，不依赖真实 Redis，
 * 保证「多实例共享同一份数据」的存储契约可在 CI 中验证。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class RedisCustomEndpointStoreTest {

    private static final String KEY = "ai:custom-endpoints";

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisCustomEndpointStore store;

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

        store = new RedisCustomEndpointStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderEndpointId() {
        store.save("my-llm", endpoint("https://llm.internal/v1", "sk-test"));

        Map<Object, Object> bucket = data.get(KEY);
        assertThat(bucket).containsKey("my-llm");
        assertThat(bucket.get("my-llm")).isInstanceOf(String.class);
        assertThat((String) bucket.get("my-llm")).contains("\"baseUrl\":\"https://llm.internal/v1\"");
    }

    @Test
    void saveIgnoresNullIdOrConfig() {
        store.save(null, endpoint("https://a/v1", "sk-a"));
        store.save("a", null);

        assertThat(data.getOrDefault(KEY, Map.of())).isEmpty();
    }

    @Test
    void saveOverwritesSameId() {
        store.save("my-llm", endpoint("https://old/v1", "sk-old"));
        store.save("my-llm", endpoint("https://new/v1", "sk-new"));

        assertThat(store.loadAll()).hasSize(1);
        assertThat(store.loadAll().get("my-llm").getBaseUrl()).isEqualTo("https://new/v1");
    }

    @Test
    void loadAllReturnsEmptyWhenNothingStored() {
        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void loadAllDeserializesEveryEntry() {
        store.save("a", endpoint("https://a/v1", "sk-a"));
        store.save("b", endpoint("https://b/v1", "sk-b"));

        Map<String, CustomEndpointConfig> loaded = store.loadAll();

        assertThat(loaded).containsOnlyKeys("a", "b");
        assertThat(loaded.get("b").getApiKey()).isEqualTo("sk-b");
        assertThat(loaded.get("a").getModels()).containsExactly("gpt-4o");
    }

    @Test
    void loadAllSkipsCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("bad", "{not-json");
        store.save("good", endpoint("https://good/v1", "sk-good"));

        Map<String, CustomEndpointConfig> loaded = store.loadAll();

        assertThat(loaded).containsOnlyKeys("good");
    }

    @Test
    void loadAllSkipsNonStringEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("non-string", 42);

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save("a", endpoint("https://a/v1", "sk-a"));
        store.save("b", endpoint("https://b/v1", "sk-b"));

        store.delete("a");

        assertThat(store.loadAll()).containsOnlyKeys("b");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");

        assertThat(data).isEmpty();
    }

    private CustomEndpointConfig endpoint(String baseUrl, String apiKey) {
        CustomEndpointConfig config = new CustomEndpointConfig();
        config.setEnabled(true);
        config.setName("自定义端点");
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setModels(List.of("gpt-4o"));
        return config;
    }
}
