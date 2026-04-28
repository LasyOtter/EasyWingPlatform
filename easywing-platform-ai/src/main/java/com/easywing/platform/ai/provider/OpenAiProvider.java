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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class OpenAiProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "openai";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiProvider(WebClient webClient, AiProperties.OpenAiConfig config) {
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
                        .id(OpenAiModels.GPT_4O)
                        .provider(PROVIDER_NAME)
                        .name("GPT-4o")
                        .contextWindow(128000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(5.0)
                        .outputCostPer1MTokens(15.0)
                        .build(),
                ModelInfo.builder()
                        .id(OpenAiModels.GPT_4O_MINI)
                        .provider(PROVIDER_NAME)
                        .name("GPT-4o Mini")
                        .contextWindow(128000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(0.15)
                        .outputCostPer1MTokens(0.60)
                        .build()
        );
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.of(
                OpenAiModels.GPT_4O,
                OpenAiModels.GPT_4O_MINI,
                OpenAiModels.GPT_4_TURBO,
                OpenAiModels.GPT_4,
                OpenAiModels.GPT_35_TURBO
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
        String url = getApiBaseUrl() + "/chat/completions";
        String model = request.getModel();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", convertMessages(request.getMessages()));
        body.put("stream", false);

        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() > 0) {
            body.put("max_tokens", request.getMaxTokens());
        }

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> parseResponse(json, model))
                .timeout(java.time.Duration.ofSeconds(120))
                .doOnError(e -> log.error("OpenAI API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        String url = getApiBaseUrl() + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", request.getModel());
        requestBody.put("messages", convertMessages(request.getMessages()));
        requestBody.put("stream", true);

        if (request.getTemperature() > 0) {
            requestBody.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() > 0) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }

        AtomicReference<String> responseId = new AtomicReference<>("chatcmpl-" + Instant.now().getEpochSecond());
        AtomicReference<String> model = new AtomicReference<>(request.getModel());
        AtomicReference<Integer> totalTokens = new AtomicReference<>(0);

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(s -> Flux.fromArray(s.split("\n")))
                .filter(line -> !line.isEmpty() && line.startsWith("data: "))
                .map(line -> line.substring(6))
                .filter(line -> !"[DONE]".equals(line.trim()))
                .map(this::parseStreamResponse)
                .doOnNext(chunk -> {
                    if (chunk.getId() != null) {
                        responseId.set(chunk.getId());
                    }
                    if (chunk.getModel() != null) {
                        model.set(chunk.getModel());
                    }
                    if (chunk.getUsage() != null) {
                        totalTokens.updateAndGet(v -> v + chunk.getUsage().getCompletionTokens());
                    }
                })
                .doOnError(e -> log.error("OpenAI stream error: {}", e.getMessage()))
                .doOnComplete(() -> log.debug("Stream completed"));
    }

    private ChatCompletionResponse parseStreamResponse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            String id = root.has("id") ? root.get("id").asText() : null;
            String model = root.has("model") ? root.get("model").asText() : null;
            JsonNode choice = root.get("choices").get(0);
            JsonNode delta = choice.has("delta") ? choice.get("delta") : null;
            String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText() : null;

            ChatMessage deltaMsg = null;
            if (delta != null && !delta.isEmpty()) {
                deltaMsg = ChatMessage.builder()
                        .role(delta.has("role") ? delta.get("role").asText() : "assistant")
                        .content(delta.has("content") ? delta.get("content").asText() : "")
                        .build();
            }

            ChatCompletionResponse.Usage usage = null;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                usage = ChatCompletionResponse.Usage.builder()
                        .promptTokens(usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0)
                        .completionTokens(usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0)
                        .totalTokens(usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : 0)
                        .build();
            }

            return ChatCompletionResponse.builder()
                    .id(id)
                    .object("chat.completion.chunk")
                    .created(Instant.now().getEpochSecond())
                    .model(model)
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .delta(deltaMsg)
                                    .finishReason(finishReason)
                                    .build()
                    ))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse stream response: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Mono<Boolean> validateConnection() {
        return webClient.get()
                .uri(getApiBaseUrl() + "/models")
                .header("Authorization", "Bearer " + getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> json != null)
                .onErrorReturn(false);
    }

    private List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        if (messages == null) return List.of();
        return messages.stream().map(msg -> {
            Map<String, Object> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            return message;
        }).toList();
    }

    private ChatCompletionResponse parseResponse(String json, String defaultModel) {
        if (json == null || json.isEmpty()) {
            return ChatCompletionResponse.builder()
                    .id("chatcmpl-" + Instant.now().getEpochSecond())
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(defaultModel)
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .message(ChatMessage.builder()
                                            .role("assistant")
                                            .content("")
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

        try {
            JsonNode root = objectMapper.readTree(json);
            String id = root.has("id") ? root.get("id").asText() : null;
            long created = root.has("created") ? root.get("created").asLong() : Instant.now().getEpochSecond();
            String model = root.has("model") ? root.get("model").asText() : defaultModel;

            JsonNode choice = root.get("choices").get(0);
            JsonNode message = choice.get("message");
            String content = message.has("content") ? message.get("content").asText() : "";
            String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText() : "stop";

            ChatMessage assistantMessage = ChatMessage.builder()
                    .role(message.has("role") ? message.get("role").asText() : "assistant")
                    .content(content)
                    .build();

            ChatCompletionResponse.Usage usage = null;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                usage = ChatCompletionResponse.Usage.builder()
                        .promptTokens(usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0)
                        .completionTokens(usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0)
                        .totalTokens(usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : 0)
                        .build();
            }

            return ChatCompletionResponse.builder()
                    .id(id)
                    .object("chat.completion")
                    .created(created)
                    .model(model)
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .message(assistantMessage)
                                    .finishReason(finishReason)
                                    .build()
                    ))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse response: {}", e.getMessage());
            return ChatCompletionResponse.builder()
                    .id("chatcmpl-" + Instant.now().getEpochSecond())
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(defaultModel)
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .message(ChatMessage.builder()
                                            .role("assistant")
                                            .content("")
                                            .build())
                                    .finishReason("error")
                                    .build()
                    ))
                    .build();
        }
    }
}