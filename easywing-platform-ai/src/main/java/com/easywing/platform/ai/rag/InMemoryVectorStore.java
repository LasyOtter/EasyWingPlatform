package com.easywing.platform.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储 - 用于开发和测试
 * 生产环境应使用 Milvus、Pinecone 等
 */
@Slf4j
@Component
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, Collection> collections = new ConcurrentHashMap<>();

    @Override
    public Mono<String> addDocument(DocumentChunk chunk) {
        String collectionName = "default";
        Collection collection = collections.computeIfAbsent(collectionName,
                k -> new Collection(collectionName, 1536)); // OpenAI embedding dimension

        collection.addChunk(chunk);
        log.debug("Added document chunk: {}", chunk.getId());
        return Mono.just(chunk.getId());
    }

    @Override
    public Mono<List<String>> addDocuments(List<DocumentChunk> chunks) {
        return reactor.core.publisher.Flux.fromIterable(chunks)
                .flatMap(this::addDocument)
                .collectList();
    }

    @Override
    public Mono<List<DocumentChunk>> search(float[] queryEmbedding, int topK) {
        return searchWithFilter(queryEmbedding, topK, null);
    }

    @Override
    public Mono<List<DocumentChunk>> searchWithFilter(
            float[] queryEmbedding,
            int topK,
            Map<String, Object> filter) {

        String collectionName = "default";
        Collection collection = collections.get(collectionName);

        if (collection == null) {
            log.warn("Collection not found: {}", collectionName);
            return Mono.just(List.of());
        }

        List<DocumentChunk> results = collection.getChunks().stream()
                .map(chunk -> {
                    double similarity = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                    chunk.setScore(similarity);
                    return chunk;
                })
                .filter(chunk -> filter == null || matchesFilter(chunk, filter))
                .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("Vector search found {} results (topK={})", results.size(), topK);
        return Mono.just(results);
    }

    @Override
    public Mono<Void> deleteDocument(String documentId) {
        collections.values().forEach(collection ->
                collection.getChunks().removeIf(chunk -> chunk.getDocumentId().equals(documentId)));
        log.info("Deleted document: {}", documentId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteCollection(String collectionName) {
        collections.remove(collectionName);
        log.info("Deleted collection: {}", collectionName);
        return Mono.empty();
    }

    @Override
    public Mono<Void> createCollection(String collectionName, int dimension) {
        collections.put(collectionName, new Collection(collectionName, dimension));
        log.info("Created collection: {} with dimension: {}", collectionName, dimension);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> collectionExists(String collectionName) {
        return Mono.just(collections.containsKey(collectionName));
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 检查是否匹配过滤条件
     */
    private boolean matchesFilter(DocumentChunk chunk, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        Map<String, Object> metadata = chunk.getMetadata();
        if (metadata == null) {
            return false;
        }

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object value = metadata.get(entry.getKey());
            if (!Objects.equals(value, entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 集合类
     */
    private static class Collection {
        private final String name;
        private final int dimension;
        private final List<DocumentChunk> chunks = new ArrayList<>();

        public Collection(String name, int dimension) {
            this.name = name;
            this.dimension = dimension;
        }

        public void addChunk(DocumentChunk chunk) {
            chunks.add(chunk);
        }

        public List<DocumentChunk> getChunks() {
            return chunks;
        }
    }
}
