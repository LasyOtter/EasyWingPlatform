package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 迭代结果 - 记录每次思考-行动-观察的结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentIteration {
    private int iterationNumber;
    private String thought;           // 思考：下一步要做什么
    private String action;            // 行动：要调用的工具名称
    private Map<String, Object> actionInput;  // 行动输入：工具参数
    private String observation;       // 观察：工具执行结果
    private boolean finalAnswer;      // 是否是最终答案
    private String answer;            // 最终答案内容
    private long executionTimeMs;     // 执行时间
    private String error;             // 错误信息
}
