package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 线协议消息格式化。
 * <p>
 * 普通消息序列化为 {@code {"role":..., "content":"..."}}；当消息携带
 * {@link ChatMessage#getContentParts()} 时，{@code content} 序列化为内容块数组，
 * 支持 {@code text} 与 {@code image_url}（URL 或 {@code data:} base64 Data URI）。
 * <p>
 * OpenAI、Azure OpenAI 与 OpenAI 兼容的自定义端点共用此格式。
 */
public final class OpenAiContentFormatter {

    private OpenAiContentFormatter() {}

    /**
     * 序列化单条消息；未知内容块类型被忽略，全部块无效时回退为纯文本。
     */
    public static Map<String, Object> message(ChatMessage msg) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", msg.getRole());
        message.put("content", content(msg));
        if (msg.getName() != null) {
            message.put("name", msg.getName());
        }
        if (msg.getToolCallId() != null) {
            message.put("tool_call_id", msg.getToolCallId());
        }
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            message.put("tool_calls", msg.getToolCalls());
        }
        return message;
    }

    /**
     * 序列化消息内容：无内容块时返回字符串，否则返回内容块数组。
     */
    public static Object content(ChatMessage msg) {
        if (msg.getContentParts() == null || msg.getContentParts().isEmpty()) {
            return msg.getContent() == null ? "" : msg.getContent();
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        for (ChatMessage.ContentPart part : msg.getContentParts()) {
            if (part == null || part.getType() == null) {
                continue;
            }
            switch (part.getType()) {
                case "text" -> {
                    Map<String, Object> text = new HashMap<>();
                    text.put("type", "text");
                    text.put("text", part.getText() == null ? "" : part.getText());
                    parts.add(text);
                }
                case "image_url" -> {
                    ChatMessage.ImageUrl imageUrl = part.getImageUrl();
                    if (imageUrl == null
                            || imageUrl.getUrl() == null
                            || imageUrl.getUrl().isBlank()) {
                        continue;
                    }
                    Map<String, Object> url = new HashMap<>();
                    url.put("url", imageUrl.getUrl());
                    if (imageUrl.getDetail() != null) {
                        url.put("detail", imageUrl.getDetail());
                    }
                    Map<String, Object> image = new HashMap<>();
                    image.put("type", "image_url");
                    image.put("image_url", url);
                    parts.add(image);
                }
                default -> {
                    // 未知内容块类型直接忽略，避免污染请求体
                }
            }
        }
        if (parts.isEmpty()) {
            return msg.getContent() == null ? "" : msg.getContent();
        }
        return parts;
    }
}
