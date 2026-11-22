package com.easywing.platform.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryDocumentStore} 归属隔离测试。
 */
class DocumentStoreTest {

    private InMemoryDocumentStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryDocumentStore();
    }

    @Test
    @DisplayName("按用户与租户隔离列举")
    void listIsIsolatedByUserAndTenant() {
        save("d1", "u1", "t1");
        save("d2", "u1", "t2");
        save("d3", "u2", "t1");

        List<DocumentRecord> u1t1 = store.listByUser("u1", "t1").block();
        assertThat(u1t1).extracting(DocumentRecord::getId).containsExactly("d1");

        List<DocumentRecord> u2t1 = store.listByUser("u2", "t1").block();
        assertThat(u2t1).extracting(DocumentRecord::getId).containsExactly("d3");
    }

    @Test
    @DisplayName("空租户参数匹配全部租户")
    void blankTenantMatchesAll() {
        save("d1", "u1", "t1");
        save("d2", "u1", "t2");

        List<DocumentRecord> all = store.listByUser("u1", null).block();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("按 id 查找与删除")
    void findAndDelete() {
        save("d1", "u1", "t1");

        assertThat(store.findById("d1").block()).isNotNull();
        store.delete("d1").block();
        assertThat(store.findById("d1").block()).isNull();
    }

    private void save(String id, String userId, String tenantId) {
        store.save(DocumentRecord.builder()
                        .id(id)
                        .userId(userId)
                        .tenantId(tenantId)
                        .createdAt(System.currentTimeMillis())
                        .build())
                .block();
    }
}
