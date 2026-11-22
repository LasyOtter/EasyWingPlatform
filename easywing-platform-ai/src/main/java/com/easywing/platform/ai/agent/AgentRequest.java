package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    private String agentId;
    private String userId;
    private String tenantId;
    private String sessionId;

    @NotBlank
    private String prompt;

    private List<ChatMessage> messages;
    private Map<String, Object> context;

    @Valid
    private AgentConfig config;

    private boolean streaming;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentConfig {
        @Min(0)
        private int maxIterations;

        @Min(0)
        private int maxToolCalls;

        @PositiveOrZero
        private long timeoutMs;

        private boolean streamingEnabled;
        private String model;

        /** 调用方用户标识，由各 Agent 透传到 {@code ChatCompletionRequest} 以归属成本。 */
        private String userId;

        /** 调用方租户标识。 */
        private String tenantId;

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double temperature;

        private Map<String, Object> metadata; // 用于传递额外配置，如 mode

        /**
         * 允许使用的工具名白名单；为空或 null 表示不限制。
         * <p>由 Agent 定义（PRD 4.2）在执行前写入，ReAct / Function Calling 均按此过滤可用工具，
         * 未列入名单的工具不会进入提示词，执行时也会被拒绝。
         */
        private List<String> tools;
    }
}
