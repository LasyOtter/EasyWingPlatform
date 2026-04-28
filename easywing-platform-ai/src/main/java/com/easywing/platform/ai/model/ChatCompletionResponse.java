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
public class ChatCompletionResponse {

    private String id;

    private String object;

    private long created;

    private String model;

    private List<Choice> choices;

    private Usage usage;

    private String serviceTier;

    private Map<String, Object> extra;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private int index;
        private ChatMessage message;
        private ChatMessage delta;
        private String finishReason;
        private ChatMessage reasoning;
        private Map<String, Object> logprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int promptTokensDetails;
        private int completionTokensDetails;
        private List<TokenUsage> cachedTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private String type;
        private int tokens;
    }
}