package com.easywing.platform.ai.multiagent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多 Agent 请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentRequest {

    /**
     * Agent ID 列表
     */
    @NotEmpty
    private List<String> agentIds;

    /**
     * 任务描述
     */
    @NotBlank
    private String task;

    /**
     * 协作类型
     */
    private MultiAgentService.CollaborationType collaborationType;

    /**
     * 最大轮次（用于辩论模式）
     */
    @Builder.Default
    @Min(0)
    private int maxRounds = 3;

    /**
     * 模型
     */
    private String model;

    /**
     * 温度
     */
    @Builder.Default
    private double temperature = 0.7;

    /**
     * 最大工具调用次数
     */
    @Builder.Default
    @Min(0)
    private int maxToolCalls = 10;
}
