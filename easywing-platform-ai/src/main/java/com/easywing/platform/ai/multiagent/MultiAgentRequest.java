package com.easywing.platform.ai.multiagent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private List<String> agentIds;

    /**
     * 任务描述
     */
    private String task;

    /**
     * 协作类型
     */
    private MultiAgentService.CollaborationType collaborationType;

    /**
     * 最大轮次（用于辩论模式）
     */
    @Builder.Default
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
    private int maxToolCalls = 10;
}
