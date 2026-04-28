package com.easywing.platform.ai.session;

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
    private String name;
    private String model;
    private int messageCount;
    private long createdAt;
    private long updatedAt;
    private long lastActiveAt;
    private boolean archived;
}