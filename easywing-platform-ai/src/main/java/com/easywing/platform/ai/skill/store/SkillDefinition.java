package com.easywing.platform.ai.skill.store;

import com.easywing.platform.ai.skill.AgentSkill;
import com.easywing.platform.ai.skill.AgentSkill.ParameterDefinition;
import com.easywing.platform.ai.skill.SkillExecutor;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能定义快照
 * <p>
 * 与运行时 {@link AgentSkill} 的区别：不包含 {@code executor}（执行器是进程内 Bean，不可序列化），
 * 仅保存可持久化的目录字段，用于多实例共享同一份技能目录。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {

    private String id;
    private String name;
    private String description;
    private String category;
    private boolean enabled;
    private String triggerMode;
    private int priority;
    private List<String> toolNames;
    private List<String> dependencies;
    private String promptTemplate;
    private Map<String, ParameterDefinition> parameters;
    private boolean composable;
    private List<String> tags;
    private Map<String, Object> metadata;

    /**
     * 从运行时技能提取可持久化字段。
     */
    public static SkillDefinition from(AgentSkill skill) {
        return SkillDefinition.builder()
                .id(skill.getId())
                .name(skill.getName())
                .description(skill.getDescription())
                .category(skill.getCategory())
                .enabled(skill.isEnabled())
                .triggerMode(skill.getTriggerMode())
                .priority(skill.getPriority())
                .toolNames(copyOf(skill.getToolNames()))
                .dependencies(copyOf(skill.getDependencies()))
                .promptTemplate(skill.getPromptTemplate())
                .parameters(skill.getParameters() == null ? null : Map.copyOf(skill.getParameters()))
                .composable(skill.isComposable())
                .tags(copyOf(skill.getTags()))
                .metadata(skill.getMetadata() == null ? null : Map.copyOf(skill.getMetadata()))
                .build();
    }

    /**
     * 还原为运行时技能；执行器由调用方按名称解析，持久化定义不携带执行器。
     */
    public AgentSkill toAgentSkill(SkillExecutor executor) {
        return AgentSkill.builder()
                .id(id)
                .name(name)
                .description(description)
                .category(category)
                .enabled(enabled)
                .triggerMode(triggerMode)
                .priority(priority)
                .toolNames(toolNames)
                .dependencies(dependencies)
                .promptTemplate(promptTemplate)
                .parameters(parameters)
                .executor(executor)
                .composable(composable)
                .tags(tags)
                .metadata(metadata)
                .build();
    }

    private static List<String> copyOf(List<String> source) {
        return source == null ? null : List.copyOf(source);
    }
}
