package com.easywing.platform.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ChatCompletionRequest {

    @NotBlank
    private String model;

    @NotNull @NotEmpty
    @Valid
    private List<ChatMessage> messages;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double topP;

    @PositiveOrZero
    private int maxTokens;

    @PositiveOrZero
    private int maxCompletionTokens;

    @DecimalMin("-2.0")
    @DecimalMax("2.0")
    private double presencePenalty;

    @DecimalMin("-2.0")
    @DecimalMax("2.0")
    private double frequencyPenalty;

    private List<String> stop;

    private boolean stream;

    private String responseFormat;

    private String user;

    private String systemPrompt;

    private List<OpenAiTool> tools;

    private String toolChoice;

    private Map<String, Object> extraParams;

    private String provider;

    /** 调用方用户标识，由网关注入，用于用量与成本归属。 */
    private String userId;

    /** 调用方租户标识，由网关注入，用于多租户用量隔离。 */
    private String tenantId;

    private boolean thinkingEnabled;

    private Integer thinkingBudgetTokens;
}
