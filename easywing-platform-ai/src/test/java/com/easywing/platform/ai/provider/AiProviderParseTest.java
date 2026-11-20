package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provider 响应解析测试
 * <p>
 * 用官方响应结构夹具验证 Anthropic / Google 的解析，覆盖此前硬编码
 * "Response placeholder" 的问题。不发起真实网络请求。
 */
class AiProviderParseTest {

    private String firstContent(ChatCompletionResponse response) {
        return response.getChoices().get(0).getMessage().getContent();
    }

    private String finishReason(ChatCompletionResponse response) {
        return response.getChoices().get(0).getFinishReason();
    }

    // ==================== Anthropic ====================

    private AnthropicProvider anthropic() {
        AiProperties.AnthropicConfig config = new AiProperties.AnthropicConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.anthropic.com/v1");
        return new AnthropicProvider(WebClient.builder().build(), config);
    }

    private ChatCompletionResponse anthropicParse(String json) throws Exception {
        Method m = AnthropicProvider.class.getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (ChatCompletionResponse) m.invoke(anthropic(), json);
    }

    @Test
    @DisplayName("Anthropic: 解析 content 文本块与 usage")
    void parsesAnthropicContentAndUsage() throws Exception {
        String json = """
                {
                  "id": "msg_01ABC",
                  "model": "claude-3-5-sonnet-20241022",
                  "stop_reason": "end_turn",
                  "content": [
                    {"type": "text", "text": "Hello"},
                    {"type": "text", "text": "world"}
                  ],
                  "usage": {"input_tokens": 12, "output_tokens": 7}
                }
                """;

        ChatCompletionResponse r = anthropicParse(json);

        assertEquals("msg_01ABC", r.getId());
        assertEquals("claude-3-5-sonnet-20241022", r.getModel());
        assertEquals("Hello\nworld", firstContent(r));
        assertEquals("stop", finishReason(r));
        assertNotNull(r.getUsage());
        assertEquals(12, r.getUsage().getPromptTokens());
        assertEquals(7, r.getUsage().getCompletionTokens());
        assertEquals(19, r.getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("Anthropic: max_tokens 映射为 length")
    void mapsAnthropicMaxTokens() throws Exception {
        String json = """
                {"id":"m1","stop_reason":"max_tokens","content":[{"type":"text","text":"hi"}],
                 "usage":{"input_tokens":1,"output_tokens":2}}
                """;

        assertEquals("length", finishReason(anthropicParse(json)));
    }

    @Test
    @DisplayName("Anthropic: tool_use 映射为 tool_calls")
    void mapsAnthropicToolUse() throws Exception {
        String json = """
                {"id":"m1","stop_reason":"tool_use","content":[{"type":"text","text":"hi"}]}
                """;

        assertEquals("tool_calls", finishReason(anthropicParse(json)));
    }

    @Test
    @DisplayName("Anthropic: 空响应返回 error 结果而非 placeholder")
    void anthropicEmptyResponseIsNotPlaceholder() throws Exception {
        ChatCompletionResponse r = anthropicParse("");

        assertEquals("error", finishReason(r));
        assertEquals("", firstContent(r));
    }

    // ==================== Google Gemini ====================

    private GoogleAiProvider google() {
        AiProperties.GoogleConfig config = new AiProperties.GoogleConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        return new GoogleAiProvider(WebClient.builder().build(), config);
    }

    private ChatCompletionResponse googleParse(String json) throws Exception {
        Method m = GoogleAiProvider.class.getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (ChatCompletionResponse) m.invoke(google(), json);
    }

    @Test
    @DisplayName("Gemini: 解析 candidates.parts 与 usageMetadata（驼峰字段）")
    void parsesGeminiContentAndUsage() throws Exception {
        String json = """
                {
                  "candidates": [
                    {
                      "content": {"role": "model", "parts": [
                        {"text": "Hello "},
                        {"text": "Gemini"}
                      ]},
                      "finishReason": "STOP"
                    }
                  ],
                  "modelVersion": "gemini-2.0-flash",
                  "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 3, "totalTokenCount": 8}
                }
                """;

        ChatCompletionResponse r = googleParse(json);

        assertEquals("Hello Gemini", firstContent(r));
        assertEquals("gemini-2.0-flash", r.getModel());
        assertEquals("stop", finishReason(r));
        assertNotNull(r.getUsage());
        assertEquals(5, r.getUsage().getPromptTokens());
        assertEquals(3, r.getUsage().getCompletionTokens());
        assertEquals(8, r.getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("Gemini: MAX_TOKENS 映射为 length")
    void mapsGeminiMaxTokens() throws Exception {
        String json = """
                {"candidates":[{"content":{"parts":[{"text":"x"}]},"finishReason":"MAX_TOKENS"}]}
                """;

        assertEquals("length", finishReason(googleParse(json)));
    }

    @Test
    @DisplayName("Gemini: SAFETY 映射为 content_filter")
    void mapsGeminiSafety() throws Exception {
        String json = """
                {"candidates":[{"content":{"parts":[{"text":""}]},"finishReason":"SAFETY"}]}
                """;

        assertEquals("content_filter", finishReason(googleParse(json)));
    }

    @Test
    @DisplayName("Gemini: 缺少 candidates 时返回 error 结果")
    void geminiMissingCandidatesIsNotPlaceholder() throws Exception {
        ChatCompletionResponse r = googleParse("{\"usageMetadata\":{\"promptTokenCount\":1}}");

        assertEquals("error", finishReason(r));
        assertTrue(firstContent(r).isEmpty());
    }
}
