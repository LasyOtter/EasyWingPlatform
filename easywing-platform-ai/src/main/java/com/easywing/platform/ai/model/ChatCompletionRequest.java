package com.easywing.platform.ai.model;

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
public class ChatCompletionRequest {

    private String model;

    private List<ChatMessage> messages;

    private double temperature;

    private double topP;

    private int maxTokens;

    private int maxCompletionTokens;

    private double presencePenalty;

    private double frequencyPenalty;

    private List<String> stop;

    private boolean stream;

    private String responseFormat;

    private String user;

    private String systemPrompt;

    private List<ChatMessage> tools;

    private String toolChoice;

    private Map<String, Object> extraParams;

    private String provider;

    private boolean thinkingEnabled;

    private Integer thinkingBudgetTokens;
}