package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini 线协议共享适配逻辑。
 * <p>
 * Google AI Studio（{@code generativelanguage.googleapis.com}）与 Vertex AI
 * （{@code aiplatform.googleapis.com}）的 Gemini 模型使用同一套请求/响应结构：
 * 请求 {@code contents[]} + 可选 {@code systemInstruction} + {@code generationConfig}，
 * 响应 {@code candidates[0].content.parts[].text} 与 {@code usageMetadata}（驼峰计数）。
 * 这里集中实现，避免两个 Provider 各写一份。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
final class GeminiProtocol {

    static final String DEFAULT_MODEL_ID = "gemini";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeminiProtocol() {}

    /**
     * 构造 Gemini 请求体。
     *
     * @param includeGenerationConfig 是否输出 {@code generationConfig}；Google AI Studio 的非流式调用
     *                               仅在指定温度时输出，流式与 Vertex 调用始终输出
     */
    static Map<String, Object> buildRequestBody(ChatCompletionRequest request, boolean includeGenerationConfig) {
        Map<String, Object> body = new HashMap<>();
        body.put("contents", convertMessages(request.getMessages()));
        addSystemInstruction(body, request.getMessages());

        if (includeGenerationConfig) {
            Map<String, Object> generationConfig = new HashMap<>();
            if (request.getTemperature() > 0) {
                generationConfig.put("temperature", request.getTemperature());
            }
            generationConfig.put("maxOutputTokens", request.getMaxTokens() > 0 ? request.getMaxTokens() : 2048);
            body.put("generationConfig", generationConfig);
        }
        return body;
    }

    /**
     * 解析 {@code generateContent} 非流式响应。
     */
    static ChatCompletionResponse parseContentResponse(String json, String defaultModel) {
        if (json == null || json.isEmpty()) {
            return emptyResponse(defaultModel);
        }
        try {
            JsonNode root = MAPPER.readTree(json);

            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                return emptyResponse(defaultModel);
            }

            JsonNode candidate = candidates.get(0);
            StringBuilder content = new StringBuilder();
            JsonNode parts = candidate.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        content.append(part.get("text").asText());
                    }
                }
            }

            String finishReason = candidate.has("finishReason")
                    ? candidate.get("finishReason").asText()
                    : null;

            int promptTokens = 0;
            int completionTokens = 0;
            JsonNode usage = root.get("usageMetadata");
            if (usage != null) {
                promptTokens = usage.path("promptTokenCount").asInt(0);
                completionTokens = usage.path("candidatesTokenCount").asInt(0);
            }

            return ChatCompletionResponse.builder()
                    .id("gemini-" + Instant.now().getEpochSecond())
                    .object("chat.completion")
                    .created(Instant.now().getEpochSecond())
                    .model(root.has("modelVersion") ? root.get("modelVersion").asText() : defaultModel)
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .index(0)
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content(content.toString())
                                    .build())
                            .finishReason(mapFinishReason(finishReason))
                            .build()))
                    .usage(ChatCompletionResponse.Usage.builder()
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .totalTokens(promptTokens + completionTokens)
                            .build())
                    .build();
        } catch (Exception e) {
            return emptyResponse(defaultModel);
        }
    }

    /**
     * 解析 {@code streamGenerateContent?alt=sse} 的单个事件。
     */
    static ChatCompletionResponse parseStreamEvent(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
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

            JsonNode usageMetadata = root.path("usageMetadata");
            ChatCompletionResponse.Usage usage = usageMetadata.isMissingNode()
                    ? null
                    : ChatCompletionResponse.Usage.builder()
                            .promptTokens(usageMetadata.path("promptTokenCount").asInt())
                            .completionTokens(
                                    usageMetadata.path("candidatesTokenCount").asInt())
                            .totalTokens(usageMetadata.path("totalTokenCount").asInt())
                            .build();

            return ChatCompletionResponse.builder()
                    .id("gemini-" + Instant.now().getEpochSecond())
                    .object("chat.completion.chunk")
                    .created(Instant.now().getEpochSecond())
                    .model(root.path("modelVersion").asText(DEFAULT_MODEL_ID))
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .index(0)
                            .delta(ChatMessage.builder()
                                    .role("model")
                                    .content(textBuilder.toString())
                                    .build())
                            .finishReason(finishReason)
                            .build()))
                    .usage(usage)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gemini {@code finishReason} 为大写，映射到 OpenAI {@code finish_reason} 语义。
     */
    static String mapFinishReason(String finishReason) {
        if (finishReason == null) {
            return "stop";
        }
        return switch (finishReason) {
            case "MAX_TOKENS" -> "length";
            case "STOP" -> "stop";
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" -> "content_filter";
            case "FINISH_REASON_UNSPECIFIED" -> null;
            default -> "stop";
        };
    }

    static ChatCompletionResponse emptyResponse(String defaultModel) {
        return ChatCompletionResponse.builder()
                .id("gemini-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(defaultModel)
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

    static List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> contents = new ArrayList<>(messages.size());
        for (ChatMessage msg : messages) {
            if (ChatMessage.ROLE_SYSTEM.equals(msg.getRole())) {
                continue;
            }
            Map<String, Object> content = new HashMap<>();
            content.put("role", "user".equals(msg.getRole()) ? "user" : "model");

            content.put("parts", geminiParts(msg));
            contents.add(content);
        }
        return contents;
    }

    /**
     * 构造 Gemini 内容块：无内容块配置时退化为文本块；图像支持 base64 Data URI
     * （{@code inlineData}）与普通 URL（{@code fileData}）。
     */
    static List<Map<String, Object>> geminiParts(ChatMessage msg) {
        if (msg.getContentParts() == null || msg.getContentParts().isEmpty()) {
            return msg.getContent() == null ? List.of() : List.of(Map.of("text", msg.getContent()));
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        for (ChatMessage.ContentPart part : msg.getContentParts()) {
            if (part == null || part.getType() == null) {
                continue;
            }
            switch (part.getType()) {
                case "text" -> parts.add(Map.of("text", part.getText() == null ? "" : part.getText()));
                case "image_url" -> {
                    Map<String, Object> image = geminiImage(part);
                    if (image != null) {
                        parts.add(image);
                    }
                }
                default -> {
                    // 未知内容块类型直接忽略
                }
            }
        }
        return parts;
    }

    private static Map<String, Object> geminiImage(ChatMessage.ContentPart part) {
        ChatMessage.ImageUrl imageUrl = part.getImageUrl();
        if (imageUrl == null || imageUrl.getUrl() == null || imageUrl.getUrl().isBlank()) {
            return null;
        }
        String url = imageUrl.getUrl();
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                return null;
            }
            String mimeType = url.substring(0, comma).substring(5).replace(";base64", "");
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", url.substring(comma + 1));
            return Map.of("inlineData", inlineData);
        }
        return Map.of("fileData", Map.of("fileUri", url));
    }

    static void addSystemInstruction(Map<String, Object> body, List<ChatMessage> messages) {
        if (messages == null) {
            return;
        }
        String system = messages.stream()
                .filter(message -> ChatMessage.ROLE_SYSTEM.equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (!system.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        }
    }
}
