package com.easywing.platform.ai.skill;

import com.easywing.platform.ai.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 技能执行上下文 - 传递给技能执行器的输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillContext {

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户输入/问题
     */
    private String input;

    /**
     * 技能参数
     */
    private Map<String, Object> parameters;

    /**
     * 对话历史
     */
    private List<ChatMessage> conversationHistory;

    /**
     * 技能链 - 依赖的其他技能
     */
    private List<String> skillChain;

    /**
     * 中间结果 - 来自前置技能的输出
     */
    private Map<String, Object> intermediateResults;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;
}
