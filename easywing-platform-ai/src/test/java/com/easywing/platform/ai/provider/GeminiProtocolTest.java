package com.easywing.platform.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Gemini 线协议共享适配测试。
 * <p>
 * Google AI Studio 与 Vertex AI 共用此逻辑，这里锁定请求体构造与 finishReason 映射。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class GeminiProtocolTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildsContentsWithRolesAndSystemInstruction() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gemini-2.0-flash-exp")
                .messages(List.of(
                        ChatMessage.builder().role("system").content("be terse").build(),
                        ChatMessage.builder().role("user").content("hello").build(),
                        ChatMessage.builder().role("assistant").content("hi").build()))
                .build();

        Map<String, Object> body = GeminiProtocol.buildRequestBody(request, false);

        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals(2, contents.size(), "system message must be moved out of contents");
        assertEquals("user", contents.get(0).get("role"));
        assertEquals("model", contents.get(1).get("role"));

        Map<String, Object> systemInstruction = (Map<String, Object>) body.get("systemInstruction");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) systemInstruction.get("parts");
        assertEquals("be terse", parts.get(0).get("text"));

        assertFalse(body.containsKey("generationConfig"), "no generationConfig when disabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsGenerationConfigWithDefaults() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gemini-2.0-flash-exp")
                .messages(
                        List.of(ChatMessage.builder().role("user").content("hi").build()))
                .temperature(0.2)
                .maxTokens(512)
                .build();

        Map<String, Object> body = GeminiProtocol.buildRequestBody(request, true);
        Map<String, Object> generationConfig = (Map<String, Object>) body.get("generationConfig");

        assertEquals(0.2, generationConfig.get("temperature"));
        assertEquals(512, generationConfig.get("maxOutputTokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsMaxOutputTokensWhenUnset() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gemini-2.0-flash-exp")
                .messages(
                        List.of(ChatMessage.builder().role("user").content("hi").build()))
                .build();

        Map<String, Object> body = GeminiProtocol.buildRequestBody(request, true);
        Map<String, Object> generationConfig = (Map<String, Object>) body.get("generationConfig");

        assertEquals(2048, generationConfig.get("maxOutputTokens"));
        assertFalse(generationConfig.containsKey("temperature"));
    }

    @Test
    void mapsFinishReasons() {
        assertEquals("stop", GeminiProtocol.mapFinishReason(null));
        assertEquals("stop", GeminiProtocol.mapFinishReason("STOP"));
        assertEquals("length", GeminiProtocol.mapFinishReason("MAX_TOKENS"));
        assertEquals("content_filter", GeminiProtocol.mapFinishReason("SAFETY"));
        assertEquals("content_filter", GeminiProtocol.mapFinishReason("BLOCKLIST"));
        assertNull(GeminiProtocol.mapFinishReason("FINISH_REASON_UNSPECIFIED"));
        assertEquals("stop", GeminiProtocol.mapFinishReason("OTHER"));
    }

    @Test
    void streamEventWithoutCandidatesReturnsNull() {
        assertNull(GeminiProtocol.parseStreamEvent("{\"usageMetadata\":{\"promptTokenCount\":1}}"));
        assertNull(GeminiProtocol.parseStreamEvent(""));
        assertNull(GeminiProtocol.parseStreamEvent("not-json"));
    }

    @Test
    void parsesStreamEventTextAndFinishReason() {
        String json =
                """
                {"candidates":[{"content":{"parts":[{"text":"chunk"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":2,"candidatesTokenCount":1,"totalTokenCount":3}}
                """;

        var chunk = GeminiProtocol.parseStreamEvent(json);

        assertEquals("chunk", chunk.getChoices().get(0).getDelta().getContent());
        assertEquals("STOP", chunk.getChoices().get(0).getFinishReason(), "stream keeps the wire value");
        assertEquals(3, chunk.getUsage().getTotalTokens());
        assertTrue(chunk.getModel().startsWith("gemini"));
    }
}
