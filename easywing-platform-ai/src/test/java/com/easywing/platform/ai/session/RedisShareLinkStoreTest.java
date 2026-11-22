package com.easywing.platform.ai.session;

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
 * {@link RedisShareLinkStore} 行为测试：Hash 字段读写、会话/用户维度过滤、排序与损坏条目容忍。
 * <p>
 * 用内存 Map 伪造 {@code StringRedisTemplate} 的 Hash 语义，不依赖真实 Redis。
 */
class RedisShareLinkStoreTest {

    private static final String KEY = "ai:share-links";

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisShareLinkStore store;

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

        store = new RedisShareLinkStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderToken() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));

        Map<Object, Object> bucket = data.get(KEY);
        assertThat(bucket).containsKey("token-1");
        assertThat((String) bucket.get("token-1")).contains("\"sessionId\":\"session-1\"");
    }

    @Test
    void saveIgnoresLinkWithoutId() {
        store.save(link(null, "session-1", "user-1", "tenant-a", 100L));
        store.save(null);

        assertThat(data.getOrDefault(KEY, Map.of())).isEmpty();
    }

    @Test
    void saveOverwritesSameToken() {
        store.save(link("token-1", "session-old", "user-1", "tenant-a", 100L));
        store.save(link("token-1", "session-new", "user-1", "tenant-a", 200L));

        assertThat(store.listByUser("user-1", "tenant-a")).hasSize(1);
        assertThat(store.listByUser("user-1", "tenant-a").get(0).getSessionId()).isEqualTo("session-new");
    }

    @Test
    void findByIdReturnsSavedLink() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));

        ShareLink loaded = store.findById("token-1");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getSessionId()).isEqualTo("session-1");
        assertThat(loaded.getMaxAccesses()).isEqualTo(5);
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        assertThat(store.findById("missing")).isNull();
    }

    @Test
    void findByIdReturnsNullForNullId() {
        assertThat(store.findById(null)).isNull();
    }

    @Test
    void findByIdReturnsNullOnCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("token-1", "{not-json");

        assertThat(store.findById("token-1")).isNull();
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));
        store.save(link("token-2", "session-2", "user-1", "tenant-a", 200L));

        store.delete("token-1");

        assertThat(store.listByUser("user-1", "tenant-a"))
                .extracting(ShareLink::getId)
                .containsExactly("token-2");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");
        store.delete(null);

        assertThat(data).isEmpty();
    }

    @Test
    void listBySessionFiltersAndSortsByCreatedAtDesc() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));
        store.save(link("token-2", "session-1", "user-1", "tenant-a", 300L));
        store.save(link("token-3", "session-2", "user-1", "tenant-a", 200L));

        List<ShareLink> links = store.listBySession("session-1");

        assertThat(links).extracting(ShareLink::getId).containsExactly("token-2", "token-1");
    }

    @Test
    void listBySessionReturnsEmptyForNullSessionId() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));

        assertThat(store.listBySession(null)).isEmpty();
    }

    @Test
    void listBySessionSkipsCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("token-bad", "{not-json");
        store.save(link("token-good", "session-1", "user-1", "tenant-a", 100L));

        assertThat(store.listBySession("session-1"))
                .extracting(ShareLink::getId)
                .containsExactly("token-good");
    }

    @Test
    void listByUserFiltersByUserAndNormalizedTenant() {
        store.save(link("token-1", "session-1", "user-1", "tenant-a", 100L));
        store.save(link("token-2", "session-2", "user-2", "tenant-a", 200L));
        store.save(link("token-3", "session-3", "user-1", "tenant-b", 300L));

        List<ShareLink> links = store.listByUser("user-1", "tenant-a");

        assertThat(links).extracting(ShareLink::getId).containsExactly("token-1");
    }

    @Test
    void listByUserTreatsBlankTenantAsDefault() {
        store.save(link("token-1", "session-1", "user-1", null, 100L));
        store.save(link("token-2", "session-2", "user-1", "tenant-a", 200L));

        List<ShareLink> links = store.listByUser("user-1", null);

        assertThat(links).extracting(ShareLink::getId).containsExactly("token-1");
    }

    private ShareLink link(String id, String sessionId, String userId, String tenantId, long createdAt) {
        return ShareLink.builder()
                .id(id)
                .sessionId(sessionId)
                .userId(userId)
                .tenantId(tenantId)
                .createdAt(createdAt)
                .expiresAt(0L)
                .revoked(false)
                .maxAccesses(5)
                .accessCount(0)
                .lastAccessAt(0L)
                .build();
    }
}
