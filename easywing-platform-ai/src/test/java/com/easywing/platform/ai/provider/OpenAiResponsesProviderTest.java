package com.easywing.platform.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Responses API 协议解析与请求体构造测试。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class OpenAiResponsesProviderTest {

    private static final String COMPLETED_JSON =
            """
            {
              "id": "resp_123",
              "model": "gpt-4o",
              "status": "completed",
              "output_text": "Hello from Responses",
              "usage": {"input_tokens": 12, "output_tokens": 7, "total_tokens": 19}
            }
            """;

    private final OpenAiResponsesProvider provider =
            new OpenAiResponsesProvider(WebClient.builder().build(), config(null));

    @Test
    void parsesTopLevelOutputTextAndUsage() {
        ChatCompletionResponse response = provider.parseResponse(COMPLETED_JSON, "gpt-4o");

        assertEquals("resp_123", response.getId());
        assertEquals("gpt-4o", response.getModel());
        assertEquals("stop", response.getChoices().get(0).getFinishReason());
        assertEquals(
                "Hello from Responses",
                response.getChoices().get(0).getMessage().getContent());
        assertEquals(12, response.getUsage().getPromptTokens());
        assertEquals(7, response.getUsage().getCompletionTokens());
        assertEquals(19, response.getUsage().getTotalTokens());
    }

    @Test
    void fallsBackToOutputContentText() {
        String json =
                """
                {
                  "id": "resp_456",
                  "status": "completed",
                  "output": [
                    {"type": "message", "content": [
                      {"type": "output_text", "text": "Part A "},
                      {"type": "output_text", "text": "Part B"}
                    ]}
                  ]
                }
                """;

        ChatCompletionResponse response = provider.parseResponse(json, "gpt-4o");

        assertEquals("Part A Part B", response.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapsIncompleteStatusToLength() {
        String json =
                """
                {"id": "resp_789", "status": "incomplete",
                 "incomplete_details": {"reason": "max_output_tokens"}, "output_text": "truncated"}
                """;

        assertEquals(
                "length",
                provider.parseResponse(json, "gpt-4o").getChoices().get(0).getFinishReason());
    }

    @Test
    void parsesTextDeltaEvent() {
        ChatCompletionResponse chunk =
                provider.parseStreamEvent("{\"type\":\"response.output_text.delta\",\"delta\":\"hi\"}");

        assertEquals("hi", chunk.getChoices().get(0).getDelta().getContent());
        assertNull(chunk.getChoices().get(0).getFinishReason());
    }

    @Test
    void parsesCompletedEventWithUsage() {
        String json =
                """
                {"type":"response.completed",
                 "response": {"id": "resp_1", "status": "completed",
                              "usage": {"input_tokens": 3, "output_tokens": 4, "total_tokens": 7}}}
                """;

        ChatCompletionResponse chunk = provider.parseStreamEvent(json);

        assertEquals("stop", chunk.getChoices().get(0).getFinishReason());
        assertEquals("resp_1", chunk.getId());
        assertEquals(7, chunk.getUsage().getTotalTokens());
    }

    @Test
    void ignoresIrrelevantStreamEvents() {
        assertNull(provider.parseStreamEvent("{\"type\":\"response.created\"}"));
        assertNull(provider.parseStreamEvent("{\"type\":\"response.output_item.added\"}"));
        assertNull(provider.parseStreamEvent(""));
    }

    @Test
    void buildsResponsesApiRequestBody() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        ChatMessage.builder().role("system").content("be terse").build(),
                        ChatMessage.builder().role("user").content("hello").build()))
                .maxTokens(2048)
                .temperature(0.3)
                .systemPrompt("global instructions")
                .build();

        Map<String, Object> body = provider.buildRequestBody(request, false);

        assertFalse(body.containsKey("messages"), "Responses API must use input, not messages");
        assertFalse(body.containsKey("max_tokens"), "Responses API must use max_output_tokens");
        assertEquals(false, body.get("stream"), "stream flag must follow the argument");

        assertEquals(2048, body.get("max_output_tokens"));
        assertEquals("global instructions", body.get("instructions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) body.get("input");
        assertEquals(2, input.size());
        assertEquals("system", input.get(0).get("role"));
        assertEquals("be terse", input.get(0).get("content"));
        assertEquals("hello", input.get(1).get("content"));
    }

    @Test
    void exposesConfiguredModelsAndFallback() {
        assertTrue(provider.supportsModel("gpt-4o"));
        assertEquals(4, provider.getSupportedModels().size());
        assertTrue(provider.getSupportedModels().get(0).isSupportsStreaming());

        AiProperties.ResponsesApiConfig config = config(List.of("my-responses-model"));
        OpenAiResponsesProvider custom =
                new OpenAiResponsesProvider(WebClient.builder().build(), config);
        assertEquals(
                List.of("my-responses-model"),
                custom.getSupportedModels().stream().map(m -> m.getId()).toList());
        assertTrue(custom.supportsModel("my-responses-model"));
        assertFalse(custom.supportsModel("gpt-4o"));
    }

    private static AiProperties.ResponsesApiConfig config(List<String> models) {
        AiProperties.ResponsesApiConfig config = new AiProperties.ResponsesApiConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setModels(models);
        return config;
    }
}
