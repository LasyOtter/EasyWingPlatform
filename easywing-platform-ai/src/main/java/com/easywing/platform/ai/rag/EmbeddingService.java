package com.easywing.platform.ai.rag;

import com.easywing.platform.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Embedding 服务 - 生成文本向量
 * <p>
 * 配置了 OpenAI API Key 时调用 {@code /v1/embeddings}（model
 * {@code easywing.ai.embedding.model}，默认 {@code text-embedding-3-small}）；
 * 未配置时退化为<b>确定性随机向量</b>，仅用于本地开发与测试 —— 该向量无语义，
 * 相似度检索结果不可用，启动时会打印 WARN。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final int FALLBACK_DIMENSION = 1536;
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final WebClient.Builder webClientBuilder;
    private final AiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    public EmbeddingService(WebClient.Builder webClientBuilder, AiProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        if (!isRemoteEmbeddingConfigured()) {
            log.warn("未配置 OpenAI API Key，EmbeddingService 使用确定性随机向量："
                    + "无语义，RAG 检索结果不可用。设置 easywing.ai.providers.openai.apiKey 启用真实 embedding。");
        }
    }

    /**
     * 生成文本 embedding
     */
    public Mono<float[]> embed(String text) {
        if (text == null) {
            return Mono.error(new IllegalArgumentException("text must not be null"));
        }
        if (text.isEmpty()) {
            return Mono.just(new float[dimension()]);
        }
        log.debug("Generating embedding for text length: {}", text.length());

        float[] cached = embeddingCache.get(text);
        if (cached != null) {
            return Mono.just(cached);
        }

        if (!isRemoteEmbeddingConfigured()) {
            return Mono.fromCallable(() -> embeddingCache.computeIfAbsent(text, this::computeFallbackEmbedding));
        }

        return callRemoteEmbedding(text)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(vector -> embeddingCache.put(text, vector));
    }

    /**
     * 批量生成 embedding
     */
    public Mono<float[][]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(new float[0][]);
        }
        return Flux.fromIterable(texts).flatMap(this::embed).collectList().map(list -> list.toArray(new float[0][]));
    }

    // ==================== 远程 embedding ====================

    private boolean isRemoteEmbeddingConfigured() {
        AiProperties.ProviderConfig openai = properties.getProviders().get("openai");
        return openai != null && StringUtils.hasText(openai.getApiKey());
    }

    private String embeddingModel() {
        String model =
                properties.getEmbedding() != null ? properties.getEmbedding().getModel() : null;
        return StringUtils.hasText(model) ? model : DEFAULT_EMBEDDING_MODEL;
    }

    /**
     * 调用 OpenAI 兼容的 embeddings 接口。
     */
    private Mono<float[]> callRemoteEmbedding(String text) {
        AiProperties.ProviderConfig openai = properties.getProviders().get("openai");
        String baseUrl = StringUtils.hasText(openai.getBaseUrl()) ? openai.getBaseUrl() : "https://api.openai.com/v1";

        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel());
        body.put("input", text);

        return webClientBuilder
                .build()
                .post()
                .uri(baseUrl + "/embeddings")
                .header("Authorization", "Bearer " + openai.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseEmbedding)
                .doOnError(e -> log.error("远程 embedding 调用失败: {}", e.getMessage()));
    }

    /**
     * 解析 {@code {"data":[{"embedding":[...]}]}。
     */
    private float[] parseEmbedding(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("embedding 响应缺少 data 数组");
            }
            JsonNode vectorNode = data.get(0).path("embedding");
            if (!vectorNode.isArray()) {
                throw new IllegalStateException("embedding 响应缺少 embedding 字段");
            }
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            normalize(vector);
            return vector;
        } catch (Exception e) {
            throw new IllegalStateException("解析 embedding 响应失败: " + e.getMessage(), e);
        }
    }

    // ==================== 本地回退 ====================

    /**
     * 以文本哈希为随机种子的确定性向量。
     * <p>
     * 仅保证同一文本向量稳定可复现，<b>不表达语义</b>：不同含义的文本不会更相近。
     */
    private float[] computeFallbackEmbedding(String text) {
        Random seeded = new Random(text.hashCode());
        float[] embedding = new float[FALLBACK_DIMENSION];
        for (int i = 0; i < FALLBACK_DIMENSION; i++) {
            embedding[i] = seeded.nextFloat() * 2 - 1;
        }
        normalize(embedding);
        return embedding;
    }

    private int dimension() {
        return FALLBACK_DIMENSION;
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
     * 测试用：清空缓存。
     */
    void clearCache() {
        embeddingCache.clear();
    }

    /**
     * 测试用：读取缓存内容。
     */
    List<String> cachedTexts() {
        return new ArrayList<>(embeddingCache.keySet());
    }
}
