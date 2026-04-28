package com.easywing.platform.ai.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, AgentSkill> skills = new ConcurrentHashMap<>();

    public void register(AgentSkill skill) {
        if (skill != null && skill.getName() != null) {
            skills.put(skill.getName(), skill);
            log.info("Registered skill: {}", skill.getName());
        }
    }

    public AgentSkill get(String name) {
        return skills.get(name);
    }

    public List<AgentSkill> getAll() {
        return List.copyOf(skills.values());
    }

    public List<AgentSkill> getByCategory(String category) {
        return skills.values().stream()
                .filter(s -> category.equals(s.getCategory()))
                .toList();
    }

    public boolean contains(String name) {
        return skills.containsKey(name);
    }

    public void unregister(String name) {
        skills.remove(name);
    }

    public int size() {
        return skills.size();
    }
}