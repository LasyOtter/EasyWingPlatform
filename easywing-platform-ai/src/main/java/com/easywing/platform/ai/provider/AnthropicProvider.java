package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class AnthropicProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "anthropic";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicProvider(WebClient webClient, AiProperties.AnthropicConfig config) {
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
                .build());
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.of(
                AnthropicModels.CLAUDE_3_5_SONNET,
                AnthropicModels.CLAUDE_3_OPUS,
                AnthropicModels.CLAUDE_3_SONNET,
                AnthropicModels.CLAUDE_3_HAiku);
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
        addSystemInstruction(body, request.getMessages());
        body.put("stream", false);
        body.put("anthropic_version", API_VERSION);

        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }

        int maxTokens = request.getMaxTokens() > 0 ? request.getMaxTokens() : 4096;
        body.put("max_tokens", maxTokens);

        if (request.isThinkingEnabled() && request.getThinkingBudgetTokens() != null) {
            body.put("thinking", Map.of("type", "enabled", "budget_tokens", request.getThinkingBudgetTokens()));
        }

        return webClient
                .post()
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
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", convertMessagesToAnthropicFormat(request.getMessages()));
        addSystemInstruction(body, request.getMessages());
        body.put("stream", true);
        body.put("max_tokens", request.getMaxTokens() > 0 ? request.getMaxTokens() : 4096);
        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }

        return webClient
                .post()
                .uri(getApiBaseUrl() + "/messages")
                .header("x-api-key", getApiKey())
                .header("anthropic-version", API_VERSION)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .map(this::parseStreamEvent)
                .filter(Objects::nonNull)
                .doOnError(e -> log.error("Anthropic stream error: {}", e.getMessage()));
    }

    private ChatCompletionResponse parseStreamEvent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText();
            String id = root.path("message").path("id").asText(UUID.randomUUID().toString());
            String model = root.path("message").path("model").asText("");
            String content = "";
            String finishReason = null;
            ChatCompletionResponse.Usage usage = null;
            if ("content_block_delta".equals(type)) {
                JsonNode delta = root.path("delta");
                if (!"text_delta".equals(delta.path("type").asText())) {
                    return null;
                }
                content = delta.path("text").asText("");
            } else if ("message_delta".equals(type)) {
                String stopReason = root.path("delta").path("stop_reason").asText("");
                finishReason = switch (stopReason) {
                    case "end_turn", "stop_sequence" -> "stop";
                    case "max_tokens" -> "length";
                    case "tool_use" -> "tool_calls";
                    default -> null;};
                JsonNode tokens = root.path("usage");
                if (tokens.has("output_tokens")) {
                    usage = ChatCompletionResponse.Usage.builder()
                            .promptTokens(0)
                            .completionTokens(tokens.path("output_tokens").asInt())
                            .totalTokens(tokens.path("output_tokens").asInt())
                            .build();
                }
            } else if (!"message_start".equals(type)) {
                return null;
            }
            ChatMessage delta =
                    ChatMessage.builder().role("assistant").content(content).build();
            return ChatCompletionResponse.builder()
                    .id(id)
                    .object("chat.completion.chunk")
                    .created(Instant.now().getEpochSecond())
                    .model(model.isBlank() ? null : model)
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .index(0)
                            .delta(delta)
                            .finishReason(finishReason)
                            .build()))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            log.debug("Ignoring malformed Anthropic stream event", e);
            return null;
        }
    }

    @Override
    public Mono<Boolean> validateConnection() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", AnthropicModels.CLAUDE_3_5_SONNET);
        body.put("max_tokens", 1);
        body.put("messages", List.of(Map.of("role", "user", "content", "hi")));

        return webClient
                .post()
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
        return messages.stream()
                .filter(msg -> !ChatMessage.ROLE_SYSTEM.equals(msg.getRole()))
                .map(msg -> {
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
                })
                .toList();
    }

    private void addSystemInstruction(Map<String, Object> body, List<ChatMessage> messages) {
        if (messages == null) {
            return;
        }
        String system = messages.stream()
                .filter(message -> ChatMessage.ROLE_SYSTEM.equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        if (!system.isBlank()) {
            body.put("system", system);
        }
    }

    /**
     * 解析 Anthropic Messages API 响应。
     * <p>
     * 响应结构：{@code content} 为内容块数组，文本块是 {@code {"type":"text","text":"..."}}；
     * 用量在 {@code usage.input_tokens} / {@code usage.output_tokens}；
     * {@code stop_reason} 取值为 {@code end_turn} / {@code max_tokens} / {@code stop_sequence} / {@code tool_use}。
     */
    private ChatCompletionResponse parseResponse(String json) {
        if (json == null || json.isEmpty()) {
            return emptyResponse();
        }
        try {
            JsonNode root = objectMapper.readTree(json);

            StringBuilder content = new StringBuilder();
            JsonNode contentBlocks = root.get("content");
            if (contentBlocks != null && contentBlocks.isArray()) {
                for (JsonNode block : contentBlocks) {
                    if (block.has("text")) {
                        if (content.length() > 0) {
                            content.append('\n');
                        }
                        content.append(block.get("text").asText());
                    }
                }
            }

            String stopReason =
                    root.has("stop_reason") ? root.get("stop_reason").asText() : null;

            ChatCompletionResponse.Usage usage = null;
            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                int inputTokens = usageNode.path("input_tokens").asInt(0);
                int outputTokens = usageNode.path("output_tokens").asInt(0);
                usage = ChatCompletionResponse.Usage.builder()
                        .promptTokens(inputTokens)
                        .completionTokens(outputTokens)
                        .totalTokens(inputTokens + outputTokens)
                        .build();
            }

            return ChatCompletionResponse.builder()
                    .id(
                            root.has("id")
                                    ? root.get("id").asText()
                                    : "msg-" + Instant.now().getEpochSecond())
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(root.has("model") ? root.get("model").asText() : null)
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .index(0)
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content(content.toString())
                                    .build())
                            .finishReason(mapStopReason(stopReason))
                            .build()))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response: {}", e.getMessage());
            return emptyResponse();
        }
    }

    /**
     * 将 Anthropic 的 stop_reason 映射为 OpenAI finish_reason 语义。
     */
    private String mapStopReason(String stopReason) {
        if (stopReason == null) {
            return "stop";
        }
        return switch (stopReason) {
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "stop_sequence", "end_turn" -> "stop";
            default -> "stop";
        };
    }

    private ChatCompletionResponse emptyResponse() {
        return ChatCompletionResponse.builder()
                .id("msg-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .choices(List.of(ChatCompletionResponse.Choice.builder()
                        .index(0)
                        .message(ChatMessage.builder()
                                .role("assistant")
                                .content("")
                                .build())
                        .finishReason("error")
                        .build()))
                .build();
    }
}
