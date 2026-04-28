package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
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

@Slf4j
public class AnthropicProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "anthropic";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";

    public AnthropicProvider(WebClient webClient, AiProperties.AnthropicConfig config) {
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
                        .id(AnthropicModels.CLAUDE_3_5_SONNET)
                        .provider(PROVIDER_NAME)
                        .name("Claude 3.5 Sonnet")
                        .contextWindow(200000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .supportsThinking(true)
                        .inputCostPer1MTokens(3.0)
                        .outputCostPer1MTokens(15.0)
                        .build()
        );
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.of(
                AnthropicModels.CLAUDE_3_5_SONNET,
                AnthropicModels.CLAUDE_3_OPUS,
                AnthropicModels.CLAUDE_3_SONNET,
                AnthropicModels.CLAUDE_3_HAiku
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
        String url = getApiBaseUrl() + "/messages";

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", convertMessagesToAnthropicFormat(request.getMessages()));
        body.put("stream", false);
        body.put("anthropic_version", API_VERSION);

        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }

        int maxTokens = request.getMaxTokens() > 0 ? request.getMaxTokens() : 4096;
        body.put("max_tokens", maxTokens);

        if (request.isThinkingEnabled() && request.getThinkingBudgetTokens() != null) {
            body.put("thinking", Map.of(
                    "type", "enabled",
                    "budget_tokens", request.getThinkingBudgetTokens()
            ));
        }

        return webClient.post()
                .uri(url)
                .header("x-api-key", getApiKey())
                .header("anthropic-version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .timeout(java.time.Duration.ofSeconds(120))
                .doOnError(e -> log.error("Anthropic API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        return Flux.empty();
    }

    @Override
    public Mono<Boolean> validateConnection() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", AnthropicModels.CLAUDE_3_5_SONNET);
        body.put("max_tokens", 1);
        body.put("messages", List.of(Map.of("role", "user", "content", "hi")));

        return webClient.post()
                .uri(getApiBaseUrl() + "/messages")
                .header("x-api-key", getApiKey())
                .header("anthropic-version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> json != null)
                .onErrorReturn(false);
    }

    private List<Map<String, Object>> convertMessagesToAnthropicFormat(List<ChatMessage> messages) {
        if (messages == null) return List.of();
        return messages.stream().map(msg -> {
            Map<String, Object> message = new HashMap<>();
            String role = msg.getRole();
            if ("user".equals(role)) {
                message.put("role", "user");
            } else if ("assistant".equals(role)) {
                message.put("role", "assistant");
            } else {
                message.put("role", role);
            }
            if (msg.getContent() != null) {
                message.put("content", msg.getContent());
            }
            return message;
        }).toList();
    }

    private ChatCompletionResponse parseResponse(String json) {
        return ChatCompletionResponse.builder()
                .id("msg-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model("claude-3-5-sonnet")
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