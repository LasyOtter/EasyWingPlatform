package com.easywing.platform.ai.rag;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 向量存储接口 - 支持多种向量数据库
 */
public interface VectorStore {

    /**
     * 添加文档
     */
    Mono<String> addDocument(DocumentChunk chunk);

    /**
     * 批量添加文档
     */
    Mono<List<String>> addDocuments(List<DocumentChunk> chunks);

    /**
     * 搜索相似文档
     */
    Mono<List<DocumentChunk>> search(float[] queryEmbedding, int topK);

    /**
     * 搜索相似文档（带过滤条件）
     */
    Mono<List<DocumentChunk>> searchWithFilter(float[] queryEmbedding, int topK, Map<String, Object> filter);

    /**
     * 删除文档
     */
    Mono<Void> deleteDocument(String documentId);

    /**
     * 删除集合
     */
    Mono<Void> deleteCollection(String collectionName);

    /**
     * 创建集合
     */
    Mono<Void> createCollection(String collectionName, int dimension);

    /**
     * 检查集合是否存在
     */
    Mono<Boolean> collectionExists(String collectionName);
}
