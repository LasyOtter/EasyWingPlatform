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
public class AgentRequest {

    private String agentId;
    private String userId;
    private String sessionId;
    private String prompt;
    private List<ChatMessage> messages;
    private Map<String, Object> context;
    private AgentConfig config;
    private boolean streaming;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentConfig {
        private int maxIterations;
        private int maxToolCalls;
        private long timeoutMs;
        private boolean streamingEnabled;
        private String model;
        private double temperature;
    }
}