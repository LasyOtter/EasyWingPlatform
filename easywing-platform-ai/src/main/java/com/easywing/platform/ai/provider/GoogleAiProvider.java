package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ModelInfo;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class GoogleAiProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "google";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    public GoogleAiProvider(WebClient webClient, AiProperties.GoogleConfig config) {
        super(
                webClient,
                Map.of(
                        "apiKey",
                        config.getApiKey(),
                        "baseUrl",
                        config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL));
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return getSupportedModelIds().contains(model);
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return List.of(ModelInfo.builder()
                .id(GoogleModels.GEMINI_2_FLASH)
                .provider(PROVIDER_NAME)
                .name("Gemini 2.0 Flash")
                .contextWindow(1000000)
                .supportsStreaming(true)
                .supportsTools(true)
                .supportsVision(true)
                .inputCostPer1MTokens(0.0)
                .outputCostPer1MTokens(0.0)
                .build());
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.of(GoogleModels.GEMINI_2_FLASH, GoogleModels.GEMINI_1_5_PRO, GoogleModels.GEMINI_1_5_FLASH);
    }

    @Override
    protected String getApiBaseUrl() {
        return config.get("baseUrl");
    }

    @Override
    protected String getApiKey() {
        return config.get("apiKey");
    }

    @Override
    public Mono<ChatCompletionResponse> chat(ChatCompletionRequest request) {
        String model = request.getModel();
        String url = getApiBaseUrl() + "/" + model + ":generateContent?key=" + getApiKey();

        Map<String, Object> body = GeminiProtocol.buildRequestBody(request, request.getTemperature() > 0);

        return webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> GeminiProtocol.parseContentResponse(json, model))
                .timeout(java.time.Duration.ofSeconds(120))
                .doOnError(e -> log.error("Google AI API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        String model = request.getModel();
        String url = getApiBaseUrl() + "/" + model + ":streamGenerateContent?key=" + getApiKey() + "&alt=sse";

        Map<String, Object> requestBody =
                GeminiProtocol.buildRequestBody(request, request.getTemperature() > 0 || request.getMaxTokens() > 0);

        final String finalModel = model;
        final long created = Instant.now().getEpochSecond();
        final AtomicInteger promptTokens = new AtomicInteger(0);
        final AtomicInteger completionTokens = new AtomicInteger(0);

        return webClient
                .post()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
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
                .doOnComplete(() -> log.debug("Google AI stream completed"))
                .doOnError(e -> log.error("Google AI stream error: {}", e.getMessage()));
    }

    @Override
    public Mono<Boolean> validateConnection() {
        String url = getApiBaseUrl() + "?key=" + getApiKey();
        return webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> json != null)
                .onErrorReturn(false);
    }
}
