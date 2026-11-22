package com.easywing.platform.ai.session;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {
    private String id;
    private String userId;
    private String tenantId;
    private String name;
    private String model;
    private String provider;

    /** Fork 来源会话 ID；非 Fork 会话为 null */
    private String parentSessionId;

    /** Fork 时保留到的消息下标（含），用于分支差异比较；未指定 forkPoint 时为 null */
    private Integer forkedAtMessageIndex;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    private int messageCount;
    private long createdAt;
    private long updatedAt;
    private long lastActiveAt;
    private boolean archived;
}
