package com.easywing.platform.ai.skill;

import com.easywing.platform.ai.skill.store.SkillDefinition;
import com.easywing.platform.ai.skill.store.SkillDefinitionStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, AgentSkill> skills = new ConcurrentHashMap<>();
    private final SkillDefinitionStore definitionStore;

    public SkillRegistry(SkillDefinitionStore definitionStore) {
        this.definitionStore = definitionStore;
    }

    /**
     * 注册技能并写回持久化存储，使多实例共享同一份技能目录。
     */
    public void register(AgentSkill skill) {
        if (skill != null && skill.getName() != null) {
            skills.put(skill.getName(), skill);
            definitionStore.save(SkillDefinition.from(skill));
            log.info("Registered skill: {}", skill.getName());
        }
    }

    /**
     * 仅在技能不存在时注册，用于内置技能播种，避免覆盖管理员已持久化的启停与配置修改。
     */
    public void registerIfAbsent(AgentSkill skill) {
        if (skill != null && skill.getName() != null && !skills.containsKey(skill.getName())) {
            skills.put(skill.getName(), skill);
            definitionStore.save(SkillDefinition.from(skill));
            log.info("Registered built-in skill: {}", skill.getName());
        }
    }

    /**
     * 从持久化存储恢复技能定义；不写回存储。
     *
     * @param definitions 持久化定义
     * @param executorResolver 按技能名解析进程内执行器，解析不到返回 null（提示词型技能）
     */
    public void restore(List<SkillDefinition> definitions, Function<String, SkillExecutor> executorResolver) {
        for (SkillDefinition definition : definitions) {
            if (definition.getName() == null || skills.containsKey(definition.getName())) {
                continue;
            }
            SkillExecutor executor = executorResolver.apply(definition.getName());
            skills.put(definition.getName(), definition.toAgentSkill(executor));
            log.info("Restored skill '{}'", definition.getName());
        }
    }

    public AgentSkill get(String name) {
        return skills.get(name);
    }

    public List<AgentSkill> getAll() {
        return List.copyOf(skills.values());
    }

    public List<AgentSkill> getAlwaysOnSkills() {
        return skills.values().stream()
                .filter(AgentSkill::isEnabled)
                .filter(skill -> "ALWAYS_ON".equalsIgnoreCase(skill.getTriggerMode()))
                .toList();
    }

    public List<AgentSkill> getAutomaticSkills() {
        return skills.values().stream()
                .filter(AgentSkill::isEnabled)
                .filter(skill -> "AUTO".equalsIgnoreCase(skill.getTriggerMode()))
                .toList();
    }

    public List<AgentSkill> getByCategory(String category) {
        return skills.values().stream()
                .filter(s -> category.equals(s.getCategory()))
                .toList();
    }

    public boolean contains(String name) {
        return skills.containsKey(name);
    }

    /**
     * 注销技能并同步删除持久化定义。
     */
    public void unregister(String name) {
        skills.remove(name);
        definitionStore.delete(name);
    }

    public int size() {
        return skills.size();
    }
}
