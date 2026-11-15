package com.easywing.platform.ai.skill;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 技能定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkill {

    /**
     * 技能唯一标识
     */
    private String id;

    /**
     * 技能名称
     */
    @NotBlank
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 技能分类
     */
    private String category;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 优先级（数字越大优先级越高）
     */
    private int priority;

    /**
     * 依赖的工具名称列表
     */
    private List<String> toolNames;

    /**
     * 依赖的其他技能
     */
    private List<String> dependencies;

    /**
     * 提示词模板
     */
    private String promptTemplate;

    /**
     * 输入参数定义
     */
    private Map<String, ParameterDefinition> parameters;

    /**
     * 技能执行器
     */
    private SkillExecutor executor;

    /**
     * 是否可组合
     */
    private boolean composable;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 参数定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDefinition {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
    }
}
