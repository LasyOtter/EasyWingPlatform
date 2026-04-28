package com.easywing.platform.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    private String conversationId;

    private String userId;

    private String sessionId;

    private List<ChatMessage> messages;

    private List<ToolResult> toolResults;

    private Map<String, Object> variables;

    private String model;

    private double temperature;

    private int maxTokens;

    private long createdAt;

    private long updatedAt;

    private long expiresAt;

    private long messageCount;

    private long totalTokens;

    private String summary;

    private boolean archived;

    private Map<String, String> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private String result;
        private boolean success;
        private long executionTimeMs;
        private String error;
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.messageCount++;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public void addToolResult(ToolResult toolResult) {
        this.toolResults.add(toolResult);
        this.updatedAt = Instant.now().toEpochMilli();
    }
}