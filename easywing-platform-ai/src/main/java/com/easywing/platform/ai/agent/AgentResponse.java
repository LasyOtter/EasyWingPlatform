package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String id;
    private String agentId;
    private String sessionId;
    private ChatMessage message;
    private List<ChatMessage> messages;
    private boolean completed;
    private String finishReason;
    private Map<String, Object> metadata;
}