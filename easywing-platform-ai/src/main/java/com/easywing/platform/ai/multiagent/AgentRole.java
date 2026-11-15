package com.easywing.platform.ai.multiagent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 角色定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRole {

    /**
     * 角色 ID
     */
    private String id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 能力列表
     */
    private List<String> capabilities;

    /**
     * 专业领域
     */
    private List<String> domains;

    /**
     * 优先级
     */
    private int priority;
}
