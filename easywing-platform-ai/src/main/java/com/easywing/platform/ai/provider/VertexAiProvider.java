package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ModelInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Vertex AI（Gemini on Vertex）提供方。
 * <p>
 * 与 Google AI Studio 共用 Gemini 线协议，差别仅在鉴权与端点：
 * Vertex 使用 OAuth2 访问令牌（{@code Authorization: Bearer <token>}），
 * 且 baseUrl 指向
 * {@code https://<region>-aiplatform.googleapis.com/v1/projects/<project>/locations/<region>/publishers/google/models}。
 * <p>
 * 请求体与响应解析统一委托 {@link GeminiProtocol}，避免两个 Provider 各写一份。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class VertexAiProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "vertex";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final List<String> DEFAULT_MODELS =
            List.of(GoogleModels.GEMINI_2_FLASH, GoogleModels.GEMINI_1_5_PRO, GoogleModels.GEMINI_1_5_FLASH);

    private final List<String> configuredModels;

    public VertexAiProvider(WebClient webClient, AiProperties.VertexConfig config) {
        super(
                webClient,
                Map.of(
                        "accessToken",
                        config.getAccessToken() != null ? config.getAccessToken() : "",
                        "baseUrl",
                        config.getBaseUrl() != null ? config.getBaseUrl() : ""));
        this.configuredModels = config.getModels() == null
                ? List.of()
                : config.getModels().stream()
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return effectiveModels().contains(model);
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return effectiveModels().stream()
                .map(model -> ModelInfo.builder()
                        .id(model)
                        .provider(PROVIDER_NAME)
                        .name(model)
                        .contextWindow(1000000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(0.0)
                        .outputCostPer1MTokens(0.0)
                        .build())
                .toList();
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.copyOf(effectiveModels());
    }

    private List<String> effectiveModels() {
        return configuredModels.isEmpty() ? DEFAULT_MODELS : configuredModels;
    }

    @Override
    protected String getApiBaseUrl() {
        return config.get("baseUrl");
    }

    @Override
    protected String getApiKey() {
        return config.get("accessToken");
    }

    /**
     * 拼接 Vertex Gemini 调用地址。
     * <p>
     * 非流式为 {@code {baseUrl}/{model}:generateContent}，
     * 流式为 {@code {baseUrl}/{model}:streamGenerateContent?alt=sse}。
     * baseUrl 需已包含 {@code .../publishers/google/models}。
     */
    String buildUrl(String model, boolean stream) {
        String base = getApiBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("Vertex AI baseUrl is not configured");
        }
        String trimmed = base.replaceAll("/+$", "");
        return trimmed + "/" + model + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent");
    }

    @Override
    public Mono<ChatCompletionResponse> chat(ChatCompletionRequest request) {
        String model = request.getModel();

        return webClient
                .post()
                .uri(buildUrl(model, false))
                .headers(headers -> headers.setBearerAuth(getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GeminiProtocol.buildRequestBody(request, request.getTemperature() > 0))
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> GeminiProtocol.parseContentResponse(json, model))
                .timeout(DEFAULT_TIMEOUT)
                .doOnError(e -> log.error("Vertex AI API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        String model = request.getModel();
        final long created = Instant.now().getEpochSecond();
        final AtomicInteger promptTokens = new AtomicInteger(0);
        final AtomicInteger completionTokens = new AtomicInteger(0);

        return webClient
                .post()
                .uri(buildUrl(model, true))
                .headers(headers -> headers.setBearerAuth(getApiKey()))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GeminiProtocol.buildRequestBody(request, true))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !"[DONE]".equals(data.trim()))
                .map(GeminiProtocol::parseStreamEvent)
                .filter(Objects::nonNull)
                .doOnNext(chunk -> {
                    if (chunk.getUsage() != null) {
                        promptTokens.updateAndGet(v -> v + chunk.getUsage().getPromptTokens());
                        completionTokens.updateAndGet(v -> v + chunk.getUsage().getCompletionTokens());
                    }
                })
                .doOnComplete(() -> log.debug("Vertex AI stream completed"))
                .doOnError(e -> log.error("Vertex AI stream error: {}", e.getMessage()));
    }

    /**
     * Vertex 没有廉价的探活端点：{@code generateContent} 需要真实请求体且按量计费。
     * 因此仅在配置了访问令牌时视为可用，避免误报连接失败。
     */
    @Override
    public Mono<Boolean> validateConnection() {
        return Mono.just(StringUtils.hasText(getApiKey()));
    }
}
