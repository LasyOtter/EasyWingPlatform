package com.easywing.platform.ai.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link RedisWorkspaceStore} 行为测试：Hash 字段读写、归属过滤、计数、排序与损坏条目容忍。
 * <p>
 * 用内存 Map 伪造 {@code StringRedisTemplate} 的 Hash 语义，不依赖真实 Redis。
 */
class RedisWorkspaceStoreTest {

    private static final String KEY = "ai:workspaces";

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisWorkspaceStore store;

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

        store = new RedisWorkspaceStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderId() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));

        Map<Object, Object> bucket = data.get(KEY);
        assertThat(bucket).containsKey("ws-1");
        assertThat((String) bucket.get("ws-1")).contains("\"name\":\"ws-a\"");
    }

    @Test
    void saveIgnoresWorkspaceWithoutId() {
        store.save(workspace(null, "ws-a", "user-1", "tenant-a", 100L));
        store.save(null);

        assertThat(data.getOrDefault(KEY, Map.of())).isEmpty();
    }

    @Test
    void saveOverwritesSameId() {
        store.save(workspace("ws-1", "旧", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-1", "新", "user-1", "tenant-a", 200L));

        assertThat(store.listAll()).hasSize(1);
        assertThat(store.listAll().get(0).getName()).isEqualTo("新");
    }

    @Test
    void findByIdReturnsSavedWorkspace() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));

        Workspace loaded = store.findById("ws-1");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getType()).isEqualTo(WorkspaceType.PERSONAL.name());
        assertThat(loaded.getOwnerId()).isEqualTo("user-1");
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        assertThat(store.findById("missing")).isNull();
    }

    @Test
    void findByIdReturnsNullOnCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("ws-1", "{not-json");

        assertThat(store.findById("ws-1")).isNull();
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-2", "ws-b", "user-1", "tenant-a", 200L));

        store.delete("ws-1");

        assertThat(store.listAll()).extracting(Workspace::getId).containsExactly("ws-2");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");

        assertThat(data).isEmpty();
    }

    @Test
    void listAllReturnsEveryEntry() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-2", "ws-b", "user-2", "tenant-b", 200L));

        assertThat(store.listAll()).extracting(Workspace::getId).containsExactlyInAnyOrder("ws-1", "ws-2");
    }

    @Test
    void listAllKeepsSlotForCorruptEntry() {
        data.computeIfAbsent(KEY, key -> new HashMap<>()).put("ws-bad", "{not-json");
        store.save(workspace("ws-good", "ws-good", "user-1", "tenant-a", 100L));

        assertThat(store.listAll()).hasSize(2);
        assertThat(store.listAll())
                .filteredOn(java.util.Objects::nonNull)
                .extracting(Workspace::getId)
                .containsExactly("ws-good");
    }

    @Test
    void listByOwnerFiltersByUserAndTenant() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-2", "ws-b", "user-2", "tenant-a", 200L));
        store.save(workspace("ws-3", "ws-c", "user-1", "tenant-b", 300L));

        assertThat(store.listByOwner("user-1", "tenant-a"))
                .extracting(Workspace::getId)
                .containsExactly("ws-1");
    }

    @Test
    void listByOwnerSortsByUpdatedAtDesc() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-2", "ws-b", "user-1", "tenant-a", 300L));
        store.save(workspace("ws-3", "ws-c", "user-1", "tenant-a", 200L));

        assertThat(store.listByOwner("user-1", "tenant-a"))
                .extracting(Workspace::getId)
                .containsExactly("ws-2", "ws-3", "ws-1");
    }

    @Test
    void listByOwnerTreatsBlankTenantAsDefault() {
        store.save(workspace("ws-1", "ws-a", "user-1", null, 100L));
        store.save(workspace("ws-2", "ws-b", "user-1", "tenant-a", 200L));

        assertThat(store.listByOwner("user-1", null))
                .extracting(Workspace::getId)
                .containsExactly("ws-1");
    }

    @Test
    void countByOwnerCountsMatchingWorkspaces() {
        store.save(workspace("ws-1", "ws-a", "user-1", "tenant-a", 100L));
        store.save(workspace("ws-2", "ws-b", "user-1", "tenant-a", 200L));
        store.save(workspace("ws-3", "ws-c", "user-2", "tenant-a", 300L));
        store.save(workspace("ws-4", "ws-d", "user-1", "tenant-b", 400L));

        assertThat(store.countByOwner("user-1", "tenant-a")).isEqualTo(2);
    }

    private Workspace workspace(String id, String name, String ownerId, String tenantId, long updatedAt) {
        return Workspace.builder()
                .id(id)
                .name(name)
                .description("描述-" + name)
                .type(WorkspaceType.PERSONAL.name())
                .ownerId(ownerId)
                .tenantId(tenantId)
                .createdAt(updatedAt - 10L)
                .updatedAt(updatedAt)
                .build();
    }
}
