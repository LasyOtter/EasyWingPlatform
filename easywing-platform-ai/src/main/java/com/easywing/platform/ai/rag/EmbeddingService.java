package com.easywing.platform.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 服务 - 生成文本向量
 * 简化实现，生产环境应调用 OpenAI Embedding API
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final int EMBEDDING_DIMENSION = 1536; // OpenAI embedding dimension
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    /**
     * 生成文本 embedding
     * <p>
     * 注意：这是确定性的模拟实现（相同文本产生相同向量），仅用于本地开发。
     * 生产环境必须替换为真实 embedding 服务，否则语义检索结果不可用。
     */
    public Mono<float[]> embed(String text) {
        if (text == null) {
            return Mono.error(new IllegalArgumentException("text must not be null"));
        }
        log.debug("Generating embedding for text length: {}", text.length());

        return Mono.fromCallable(() -> embeddingCache.computeIfAbsent(text, this::computeEmbedding));
    }

    /**
     * 以文本哈希作为随机种子，保证同一文本的向量稳定可复现
     */
    private float[] computeEmbedding(String text) {
        Random seeded = new Random(text.hashCode());
        float[] embedding = new float[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding[i] = seeded.nextFloat() * 2 - 1; // [-1, 1]
        }
        normalize(embedding);
        return embedding;
    }

    /**
     * 批量生成 embedding
     */
    public Mono<float[][]> embedBatch(java.util.List<String> texts) {
        return reactor.core.publisher.Flux.fromIterable(texts)
                .flatMap(this::embed)
                .collectList()
                .map(list -> list.toArray(new float[0][]));
    }

    /**
     * 归一化向量
     */
    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * TODO: 生产环境实现
     *
     * public Mono<float[]> embed(String text) {
     *     return webClient.post()
     *             .uri("https://api.openai.com/v1/embeddings")
     *             .header("Authorization", "Bearer " + apiKey)
     *             .bodyValue(Map.of(
     *                     "model", "text-embedding-3-small",
     *                     "input", text
     *             ))
     *             .retrieve()
     *             .bodyToMono(EmbeddingResponse.class)
     *             .map(response -> response.getData().get(0).getEmbedding());
     * }
     */
}
