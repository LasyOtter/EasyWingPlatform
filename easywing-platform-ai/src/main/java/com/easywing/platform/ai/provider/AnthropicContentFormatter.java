package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 线协议内容格式化。
 * <p>
 * 普通消息的 {@code content} 为字符串；携带 {@link ChatMessage#getContentParts()} 时序列化为
 * 内容块数组，支持 {@code text} 块与 {@code image} 块。图片来源支持 base64 Data URI
 * （{@code {"type":"base64",...}}）与普通 URL（{@code {"type":"url",...}}）。
 */
public final class AnthropicContentFormatter {

    private AnthropicContentFormatter() {}

    /**
     * 序列化消息内容：无内容块时返回字符串，否则返回 Anthropic 内容块数组。
     */
    public static Object content(ChatMessage msg) {
        if (msg.getContentParts() == null || msg.getContentParts().isEmpty()) {
            return msg.getContent() == null ? "" : msg.getContent();
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (ChatMessage.ContentPart part : msg.getContentParts()) {
            if (part == null || part.getType() == null) {
                continue;
            }
            switch (part.getType()) {
                case "text" -> {
                    Map<String, Object> text = new HashMap<>();
                    text.put("type", "text");
                    text.put("text", part.getText() == null ? "" : part.getText());
                    blocks.add(text);
                }
                case "image_url" -> {
                    Map<String, Object> source = imageSource(part);
                    if (source == null) {
                        continue;
                    }
                    Map<String, Object> image = new HashMap<>();
                    image.put("type", "image");
                    image.put("source", source);
                    blocks.add(image);
                }
                default -> {
                    // 未知内容块类型直接忽略
                }
            }
        }
        if (blocks.isEmpty()) {
            return msg.getContent() == null ? "" : msg.getContent();
        }
        return blocks;
    }

    private static Map<String, Object> imageSource(ChatMessage.ContentPart part) {
        ChatMessage.ImageUrl imageUrl = part.getImageUrl();
        if (imageUrl == null || imageUrl.getUrl() == null || imageUrl.getUrl().isBlank()) {
            return null;
        }
        String url = imageUrl.getUrl();
        Map<String, Object> source = new HashMap<>();
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                return null;
            }
            String header = url.substring(0, comma);
            String mediaType = header.startsWith("data:") ? header.substring(5) : header;
            mediaType = mediaType.replace(";base64", "");
            source.put("type", "base64");
            source.put("media_type", mediaType);
            source.put("data", url.substring(comma + 1));
        } else {
            source.put("type", "url");
            source.put("url", url);
        }
        return source;
    }
}
