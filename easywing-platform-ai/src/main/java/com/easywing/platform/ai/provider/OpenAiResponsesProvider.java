package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI Responses API 提供方。
 * <p>
 * 与 Chat Completions 是不同的协议：请求 {@code POST /v1/responses}，消息放在 {@code input}，
 * 输出在 {@code output} / {@code output_text}，用量字段为 {@code input_tokens} / {@code output_tokens}，
 * 流式事件为 {@code response.output_text.delta} 等。这里做协议适配，向上仍暴露统一的
 * {@link ChatCompletionResponse}，使业务层与网关无需区分协议。
 * <p>
 * 默认关闭，凭据只经配置或环境变量注入。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class OpenAiResponsesProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "openai-responses";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final List<String> DEFAULT_MODELS = List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> models;

    public OpenAiResponsesProvider(WebClient webClient, AiProperties.ResponsesApiConfig config) {
        super(
                webClient,
                Map.of(
                        "apiKey",
                        config.getApiKey() != null ? config.getApiKey() : "",
                        "baseUrl",
                        StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl() : DEFAULT_BASE_URL));
        List<String> configured = config.getModels();
        this.models = configured == null || configured.isEmpty() ? DEFAULT_MODELS : List.copyOf(configured);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return models.contains(model);
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return models.stream()
                .map(model -> ModelInfo.builder()
                        .id(model)
                        .provider(PROVIDER_NAME)
                        .name("Responses / " + model)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .build())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.copyOf(models);
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
        return getWebClient()
                .post()
                .uri(getApiBaseUrl() + "/responses")
                .headers(headers -> headers.setBearerAuth(getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(request, false))
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> parseResponse(json, request.getModel()))
                .timeout(java.time.Duration.ofSeconds(120))
                .doOnError(e -> log.error("OpenAI Responses API error: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        return getWebClient()
                .post()
                .uri(getApiBaseUrl() + "/responses")
                .headers(headers -> headers.setBearerAuth(getApiKey()))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(request, true))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !"[DONE]".equals(data.trim()))
                .map(this::parseStreamEvent)
                .filter(Objects::nonNull)
                .doOnError(e -> log.error("OpenAI Responses stream error: {}", e.getMessage()))
                .doOnComplete(() -> log.debug("OpenAI Responses stream completed"));
    }

    @Override
    public Mono<Boolean> validateConnection() {
        return getWebClient()
                .get()
                .uri(getApiBaseUrl() + "/models")
                .headers(headers -> headers.setBearerAuth(getApiKey()))
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> json != null)
                .onErrorReturn(false);
    }

    /**
     * 构造 Responses API 请求体。
     * <p>
     * 与 Chat Completions 的差异：消息字段为 {@code input}，最大输出字段为 {@code max_output_tokens}。
     */
    Map<String, Object> buildRequestBody(ChatCompletionRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("input", convertMessages(request.getMessages()));
        body.put("stream", stream);

        int maxOutput = request.getMaxTokens() > 0 ? request.getMaxTokens() : request.getMaxCompletionTokens();
        if (maxOutput > 0) {
            body.put("max_output_tokens", maxOutput);
        }
        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() > 0) {
            body.put("top_p", request.getTopP());
        }
        if (StringUtils.hasText(request.getSystemPrompt())) {
            body.put("instructions", request.getSystemPrompt());
        }
        return body;
    }

    private List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> input = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent() == null ? "" : message.getContent());
            input.add(item);
        }
        return input;
    }

    /**
     * 解析非流式 Responses API 响应。
     */
    ChatCompletionResponse parseResponse(String json, String defaultModel) {
        if (json == null || json.isEmpty()) {
            return errorResponse(defaultModel);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String content = extractOutputText(root);
            String finishReason = mapStatus(root.path("status").asText());
            ChatCompletionResponse.Usage usage = parseUsage(root.get("usage"));

            return ChatCompletionResponse.builder()
                    .id(root.path("id").asText("resp-" + Instant.now().getEpochSecond()))
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(root.path("model").asText(defaultModel))
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .index(0)
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content(content)
                                    .build())
                            .finishReason(finishReason)
                            .build()))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Responses API response: {}", e.getMessage());
            return errorResponse(defaultModel);
        }
    }

    /**
     * 解析流式事件：仅处理文本增量与结束事件，其余事件返回 {@code null} 被过滤。
     */
    ChatCompletionResponse parseStreamEvent(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText();

            if ("response.output_text.delta".equals(type)) {
                String delta = root.path("delta").asText("");
                return chunk(null, delta, null, null);
            }
            if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                JsonNode response = root.path("response");
                String finishReason = "response.completed".equals(type) ? "stop" : mapIncompleteReason(response);
                return chunk(response.path("id").asText(null), "", finishReason, parseUsage(response.get("usage")));
            }
            if ("response.failed".equals(type)) {
                return chunk(null, "", "error", null);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse Responses stream event: {}", e.getMessage());
            return null;
        }
    }

    private ChatCompletionResponse chunk(
            String id, String delta, String finishReason, ChatCompletionResponse.Usage usage) {
        return ChatCompletionResponse.builder()
                .id(id != null ? id : "resp-" + Instant.now().getEpochSecond())
                .object("chat.completion.chunk")
                .created(Instant.now().getEpochSecond())
                .choices(List.of(ChatCompletionResponse.Choice.builder()
                        .index(0)
                        .delta(ChatMessage.builder()
                                .role("assistant")
                                .content(delta)
                                .build())
                        .finishReason(finishReason)
                        .build()))
                .usage(usage)
                .build();
    }

    /**
     * 文本优先取顶层 {@code output_text}，否则拼接 {@code output[].content[].text}。
     */
    private String extractOutputText(JsonNode root) {
        if (root.hasNonNull("output_text")) {
            return root.get("output_text").asText();
        }
        StringBuilder text = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText()) || part.has("text")) {
                        text.append(part.path("text").asText());
                    }
                }
            }
        }
        return text.toString();
    }

    private ChatCompletionResponse.Usage parseUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        int prompt = usage.path("input_tokens").asInt(0);
        int completion = usage.path("output_tokens").asInt(0);
        int total = usage.path("total_tokens").asInt(prompt + completion);
        return ChatCompletionResponse.Usage.builder()
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .build();
    }

    private String mapStatus(String status) {
        if (!StringUtils.hasText(status) || "completed".equals(status)) {
            return "stop";
        }
        if ("failed".equals(status)) {
            return "error";
        }
        return "length";
    }

    private String mapIncompleteReason(JsonNode response) {
        JsonNode reason = response.path("incomplete_details").path("reason");
        if ("content_filter".equals(reason.asText())) {
            return "content_filter";
        }
        return "length";
    }

    private ChatCompletionResponse errorResponse(String model) {
        return ChatCompletionResponse.builder()
                .id("resp-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(model)
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
