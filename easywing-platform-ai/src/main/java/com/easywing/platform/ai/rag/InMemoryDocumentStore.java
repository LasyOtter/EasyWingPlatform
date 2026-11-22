package com.easywing.platform.ai.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 文档目录内存实现 - 用于开发和测试。
 * <p>
 * 数据仅存于进程内存，重启丢失且集群下不共享。与 {@link InMemoryVectorStore} 配套，
 * 生产环境应提供 Redis/数据库实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class InMemoryDocumentStore implements DocumentStore {

    private final ConcurrentHashMap<String, DocumentRecord> documents = new ConcurrentHashMap<>();

    public InMemoryDocumentStore() {
        log.warn("RAG 文档目录使用内存存储：重启即丢失，集群下不共享，禁止用于生产");
    }

    @Override
    public Mono<DocumentRecord> save(DocumentRecord record) {
        documents.put(record.getId(), record);
        return Mono.just(record);
    }

    @Override
    public Mono<DocumentRecord> findById(String documentId) {
        return Mono.justOrEmpty(documents.get(documentId));
    }

    @Override
    public Mono<List<DocumentRecord>> listByUser(String userId, String tenantId) {
        List<DocumentRecord> result = documents.values().stream()
                .filter(record -> userId != null && userId.equals(record.getUserId()))
                .filter(record -> tenantId == null || tenantId.isBlank() || tenantId.equals(record.getTenantId()))
                .sorted(Comparator.comparingLong(DocumentRecord::getCreatedAt).reversed())
                .toList();
        return Mono.just(result);
    }

    @Override
    public Mono<Void> delete(String documentId) {
        documents.remove(documentId);
        return Mono.empty();
    }

    /**
     * 供测试与运维统计使用。
     */
    public Mono<Long> count() {
        return Mono.just((long) documents.size());
    }

    /**
     * 供测试断言使用。
     */
    public Optional<DocumentRecord> peek(String documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }
}
