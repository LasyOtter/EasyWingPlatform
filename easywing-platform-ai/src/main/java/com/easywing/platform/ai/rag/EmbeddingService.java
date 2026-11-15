package com.easywing.platform.ai.rag;

import com.easywing.platform.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Embedding 服务 - 生成文本向量。
 * <p>
 * 优先调用 OpenAI 兼容的 {@code /embeddings} 端点（OpenAI、DashScope 等）；未配置 apiKey 时回退到确定性模拟实现，
 * 仅用于本地开发，此时语义检索结果不可用。
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_DIMENSION = 1536;
    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final AiProperties aiProperties;
    private final WebClient.Builder webClientBuilder;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();

    public EmbeddingService(AiProperties aiProperties, WebClient.Builder webClientBuilder) {
        this.aiProperties = aiProperties;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 生成文本 embedding
     */
    public Mono<float[]> embed(String text) {
        if (text == null) {
            return Mono.error(new IllegalArgumentException("text must not be null"));
        }

        float[] cached = embeddingCache.get(text);
        if (cached != null) {
            return Mono.just(cached);
        }

        ResolvedConfig config = resolveConfig();
        if (config == null) {
            return Mono.fromCallable(() -> cacheFallback(text));
        }

        return embedRemote(List.of(text), config)
                .map(vectors -> vectors[0])
                .doOnNext(vector -> embeddingCache.put(text, vector));
    }

    /**
     * 批量生成 embedding
     */
    public Mono<float[][]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(new float[0][]);
        }

        ResolvedConfig config = resolveConfig();
        if (config == null) {
            return Mono.fromCallable(() -> {
                float[][] vectors = new float[texts.size()][];
                for (int index = 0; index < texts.size(); index++) {
                    vectors[index] = cacheFallback(texts.get(index));
                }
                return vectors;
            });
        }

        List<List<String>> batches = partition(texts, config.batchSize());
        return Flux.fromIterable(batches)
                .concatMap(batch -> embedRemote(batch, config))
                .collectList()
                .map(batchVectors -> flatten(texts, batchVectors));
    }

    private float[][] flatten(List<String> texts, List<float[][]> batchVectors) {
        int total = 0;
        for (float[][] vectors : batchVectors) {
            total += vectors.length;
        }

        float[][] result = new float[total][];
        int index = 0;
        for (float[][] vectors : batchVectors) {
            for (float[] vector : vectors) {
                result[index++] = vector;
            }
        }

        for (int i = 0; i < texts.size() && i < result.length; i++) {
            embeddingCache.putIfAbsent(texts.get(i), result[i]);
        }
        return result;
    }

    private List<List<String>> partition(List<String> texts, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += batchSize) {
            batches.add(new ArrayList<>(texts.subList(start, Math.min(start + batchSize, texts.size()))));
        }
        return batches;
    }

    private Mono<float[][]> embedRemote(List<String> texts, ResolvedConfig config) {
        WebClient webClient = webClientBuilder.baseUrl(config.baseUrl()).build();
        Map<String, Object> requestBody = Map.of("model", config.model(), "input", texts);

        return webClient
                .post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .map(this::parseEmbeddings);
    }

    private float[][] parseEmbeddings(JsonNode root) {
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return new float[0][];
        }

        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(0)));

        float[][] vectors = new float[items.size()][];
        for (int index = 0; index < items.size(); index++) {
            JsonNode embedding = items.get(index).path("embedding");
            float[] vector = new float[embedding.size()];
            for (int dimension = 0; dimension < embedding.size(); dimension++) {
                vector[dimension] = (float) embedding.get(dimension).asDouble();
            }
            vectors[index] = vector;
        }
        return vectors;
    }

    private float[] cacheFallback(String text) {
        return embeddingCache.computeIfAbsent(text, this::computeFallbackEmbedding);
    }

    /**
     * 确定性模拟实现，仅在没有配置 apiKey 时使用。
     */
    private float[] computeFallbackEmbedding(String text) {
        if (fallbackWarningLogged.compareAndSet(false, true)) {
            log.warn("No embedding apiKey configured, using deterministic mock embeddings. "
                    + "Semantic search quality is degraded; configure easywing.ai.embedding.api-key for production.");
        }

        int dimension = aiProperties.getEmbedding().getDimension();
        if (dimension <= 0) {
            dimension = DEFAULT_DIMENSION;
        }

        Random seeded = new Random(text.hashCode());
        float[] embedding = new float[dimension];
        for (int index = 0; index < dimension; index++) {
            embedding[index] = seeded.nextFloat() * 2 - 1;
        }
        normalize(embedding);
        return embedding;
    }

    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= norm;
            }
        }
    }

    private ResolvedConfig resolveConfig() {
        AiProperties.EmbeddingConfig embedding = aiProperties.getEmbedding();
        AiProperties.ProviderConfig provider = aiProperties.getProviders().get(embedding.getProvider());

        String apiKey = firstNonBlank(embedding.getApiKey(), provider == null ? null : provider.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }

        String baseUrl = firstNonBlank(
                embedding.getBaseUrl(), provider == null ? null : provider.getBaseUrl(), DEFAULT_BASE_URL);
        String model = firstNonBlank(embedding.getModel(), DEFAULT_EMBEDDING_MODEL);
        int batchSize = embedding.getBatchSize() > 0 ? embedding.getBatchSize() : DEFAULT_BATCH_SIZE;
        int timeoutSeconds = provider != null && provider.getTimeoutSeconds() > 0
                ? provider.getTimeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;

        return new ResolvedConfig(apiKey, baseUrl, model, batchSize, timeoutSeconds);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record ResolvedConfig(String apiKey, String baseUrl, String model, int batchSize, int timeoutSeconds) {}
}
