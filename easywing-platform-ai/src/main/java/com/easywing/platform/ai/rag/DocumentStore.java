package com.easywing.platform.ai.rag;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 文档目录存储 - 保存文档归属与分块统计。
 * <p>
 * 与 {@link VectorStore} 分离：向量块可被检索，目录记录支撑按用户/租户的列举与删除。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface DocumentStore {

    Mono<DocumentRecord> save(DocumentRecord record);

    Mono<DocumentRecord> findById(String documentId);

    Mono<List<DocumentRecord>> listByUser(String userId, String tenantId);

    Mono<Void> delete(String documentId);
}
