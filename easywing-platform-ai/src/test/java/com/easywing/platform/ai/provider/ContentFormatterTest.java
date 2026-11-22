package com.easywing.platform.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 视觉内容块序列化测试（PRD 4.6）。
 * <p>
 * 锁定 OpenAI / Anthropic / Gemini 三条协议对 {@code text} 与 {@code image_url} 内容块的转换，
 * 防止 Provider 改动后图片在请求体中丢失。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ContentFormatterTest {

    private static final String DATA_URI = "data:image/png;base64,iVBORw0KGgo=";

    @Test
    @SuppressWarnings("unchecked")
    void openAiSerializesTextAndImageUrlParts() {
        ChatMessage message = message(part("text", "看这张图"), imagePart("https://example.com/a.png", "high"));

        Object content = OpenAiContentFormatter.content(message);

        List<Map<String, Object>> parts = assertInstanceOf(List.class, content);
        assertEquals(2, parts.size());
        assertEquals("text", parts.get(0).get("type"));
        assertEquals("看这张图", parts.get(0).get("text"));

        Map<String, Object> image = (Map<String, Object>) parts.get(1).get("image_url");
        assertEquals("https://example.com/a.png", image.get("url"));
        assertEquals("high", image.get("detail"));
    }

    @Test
    void openAiFallsBackToPlainTextWithoutParts() {
        ChatMessage message = ChatMessage.builder().role("user").content("你好").build();

        assertEquals("你好", OpenAiContentFormatter.content(message));
    }

    @Test
    @SuppressWarnings("unchecked")
    void openAiIgnoresUnknownOrInvalidParts() {
        ChatMessage message = ChatMessage.builder()
                .role("user")
                .content("兜底文本")
                .contentParts(List.of(
                        ChatMessage.ContentPart.builder().type("audio").build(),
                        ChatMessage.ContentPart.builder().type("image_url").build()))
                .build();

        assertEquals("兜底文本", OpenAiContentFormatter.content(message));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropicSerializesDataUriAsBase64Source() {
        ChatMessage message = message(part("text", "describe"), imagePart(DATA_URI, null));

        List<Map<String, Object>> blocks = assertInstanceOf(List.class, AnthropicContentFormatter.content(message));

        Map<String, Object> source = (Map<String, Object>) blocks.get(1).get("source");
        assertEquals("base64", source.get("type"));
        assertEquals("image/png", source.get("media_type"));
        assertEquals("iVBORw0KGgo=", source.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropicSerializesUrlSource() {
        ChatMessage message = message(imagePart("https://example.com/b.jpg", null));

        List<Map<String, Object>> blocks = assertInstanceOf(List.class, AnthropicContentFormatter.content(message));

        Map<String, Object> source = (Map<String, Object>) blocks.get(0).get("source");
        assertEquals("url", source.get("type"));
        assertEquals("https://example.com/b.jpg", source.get("url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void geminiSerializesDataUriAsInlineData() {
        ChatMessage message = message(imagePart(DATA_URI, null));

        List<Map<String, Object>> parts = GeminiProtocol.geminiParts(message);

        Map<String, Object> inline = (Map<String, Object>) parts.get(0).get("inlineData");
        assertEquals("image/png", inline.get("mimeType"));
        assertEquals("iVBORw0KGgo=", inline.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void geminiSerializesUrlAsFileData() {
        ChatMessage message = message(imagePart("https://example.com/c.jpg", null));

        List<Map<String, Object>> parts = GeminiProtocol.geminiParts(message);

        Map<String, Object> file = (Map<String, Object>) parts.get(0).get("fileData");
        assertEquals("https://example.com/c.jpg", file.get("fileUri"));
    }

    @Test
    void geminiFallsBackToTextWhenNoParts() {
        List<Map<String, Object>> parts = GeminiProtocol.geminiParts(
                ChatMessage.builder().role("user").content("hi").build());

        assertEquals(1, parts.size());
        assertEquals("hi", parts.get(0).get("text"));
        assertTrue(parts.get(0).containsKey("text"));
    }

    private static ChatMessage message(ChatMessage.ContentPart... parts) {
        return ChatMessage.builder()
                .role(ChatMessage.ROLE_USER)
                .content("拼接文本")
                .contentParts(List.of(parts))
                .build();
    }

    private static ChatMessage.ContentPart part(String type, String text) {
        return ChatMessage.ContentPart.builder().type(type).text(text).build();
    }

    private static ChatMessage.ContentPart imagePart(String url, String detail) {
        return ChatMessage.ContentPart.builder()
                .type("image_url")
                .imageUrl(ChatMessage.ImageUrl.builder().url(url).detail(detail).build())
                .build();
    }
}
