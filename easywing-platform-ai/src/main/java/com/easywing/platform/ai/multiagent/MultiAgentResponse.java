package com.easywing.platform.ai.multiagent;

import com.easywing.platform.ai.agent.AgentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多 Agent 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentResponse {

    /**
     * 协作类型
     */
    private MultiAgentService.CollaborationType collaborationType;

    /**
     * 所有 Agent 的响应
     */
    private List<AgentResponse> agentResponses;

    /**
     * 辩论轮次（仅辩论模式）
     */
    private List<MultiAgentService.DebateRound> debateRounds;

    /**
     * 最终结果
     */
    private String finalResult;

    /**
     * 总执行时间
     */
    private long totalExecutionTimeMs;
}
