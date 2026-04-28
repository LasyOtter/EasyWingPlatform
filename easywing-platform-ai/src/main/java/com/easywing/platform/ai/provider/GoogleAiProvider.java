package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class GoogleAiProvider extends AbstractAiProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROVIDER_NAME = "google";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    public GoogleAiProvider(WebClient webClient, AiProperties.GoogleConfig config) {
        super(webClient, Map.of(
                "apiKey", config.getApiKey(),
                "baseUrl", config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL
        ));
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
        return List.of(
                ModelInfo.builder()
                        .id(GoogleModels.GEMINI_2_FLASH)
                        .provider(PROVIDER_NAME)
                        .name("Gemini 2.0 Flash")
                        .contextWindow(1000000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(0.0)
                        .outputCostPer1MTokens(0.0)
                        .build()
        );
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.of(
                GoogleModels.GEMINI_2_FLASH,
                GoogleModels.GEMINI_1_5_PRO,
                GoogleModels.GEMINI_1_5_FLASH
        );
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

        Map<String, Object> body = new HashMap<>();
        body.put("contents", convertMessagesToGeminiFormat(request.getMessages()));

        if (request.getTemperature() > 0) {
            body.put("generationConfig", Map.of(
                    "temperature", request.getTemperature(),
                    "maxOutputTokens", request.getMaxTokens() > 0 ? request.getMaxTokens() : 2048
            ));
        }

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .timeout(java.time.Duration.ofSeconds(120))
                .doOnError(e -> log.error("Google AI API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        String model = request.getModel();
        String url = getApiBaseUrl() + "/" + model + ":streamGenerateContent?key=" + getApiKey() + "&alt=sse";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", convertMessagesToGeminiFormat(request.getMessages()));

        if (request.getTemperature() > 0 || request.getMaxTokens() > 0) {
            Map<String, Object> generationConfig = new HashMap<>();
            if (request.getTemperature() > 0) {
                generationConfig.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() > 0) {
                generationConfig.put("maxOutputTokens", request.getMaxTokens());
            } else {
                generationConfig.put("maxOutputTokens", 2048);
            }
            requestBody.put("generationConfig", generationConfig);
        }

        final String finalModel = model;
        final long created = Instant.now().getEpochSecond();
        final AtomicInteger promptTokens = new AtomicInteger(0);
        final AtomicInteger completionTokens = new AtomicInteger(0);

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(s -> Flux.fromArray(s.split("\n")))
                .filter(line -> !line.isEmpty() && line.startsWith("data: "))
                .map(line -> line.substring(6))
                .filter(line -> !"[DONE]".equals(line.trim()))
                .map(this::parseGeminiStreamResponse)
                .filter(response -> response != null)
                .doOnNext(chunk -> {
                    if (chunk.getUsage() != null) {
                        promptTokens.updateAndGet(v -> v + chunk.getUsage().getPromptTokens());
                        completionTokens.updateAndGet(v -> v + chunk.getUsage().getCompletionTokens());
                    }
                })
                .doOnComplete(() -> log.debug("Google AI stream completed"))
                .doOnError(e -> log.error("Google AI stream error: {}", e.getMessage()));
    }

    private ChatCompletionResponse parseGeminiStreamResponse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            JsonNode content = candidates.get(0).get("content");
            if (content == null) {
                return null;
            }

            JsonNode parts = content.get("parts");
            StringBuilder textBuilder = new StringBuilder();
            if (parts != null && !parts.isEmpty()) {
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        textBuilder.append(part.get("text").asText());
                    }
                }
            }

            String finishReason = null;
            JsonNode finishMessage = candidates.get(0).get("finishReason");
            if (finishMessage != null && !finishMessage.isNull()) {
                finishReason = finishMessage.asText();
            }

            return ChatCompletionResponse.builder()
                    .id("gemini-" + Instant.now().getEpochSecond())
                    .object("chat.completion.chunk")
                    .created(Instant.now().getEpochSecond())
                    .model("gemini")
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .delta(ChatMessage.builder()
                                            .role("model")
                                            .content(textBuilder.toString())
                                            .build())
                                    .finishReason(finishReason)
                                    .build()
                    ))
                    .usage(ChatCompletionResponse.Usage.builder()
                            .promptTokens(0)
                            .completionTokens(textBuilder.length())
                            .totalTokens(textBuilder.length())
                            .build())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Gemini stream response: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Mono<Boolean> validateConnection() {
        String url = getApiBaseUrl() + "?key=" + getApiKey();
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> json != null)
                .onErrorReturn(false);
    }

    private List<Map<String, Object>> convertMessagesToGeminiFormat(List<ChatMessage> messages) {
        if (messages == null) return List.of();
        return messages.stream().map(msg -> {
            Map<String, Object> content = new HashMap<>();
            String role = "user".equals(msg.getRole()) ? "user" : "model";
            content.put("role", role);

            List<Map<String, Object>> parts = new java.util.ArrayList<>();
            if (msg.getContent() != null) {
                parts.add(Map.of("text", msg.getContent()));
            }
            content.put("parts", parts);
            return content;
        }).toList();
    }

    private ChatCompletionResponse parseResponse(String json) {
        return ChatCompletionResponse.builder()
                .id("gemini-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model("gemini")
                .choices(List.of(
                        ChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(ChatMessage.builder()
                                        .role("assistant")
                                        .content("Response placeholder")
                                        .build())
                                .finishReason("stop")
                                .build()
                ))
                .usage(ChatCompletionResponse.Usage.builder()
                        .promptTokens(0)
                        .completionTokens(0)
                        .totalTokens(0)
                        .build())
                .build();
    }
}