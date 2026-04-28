package com.easywing.platform.ai.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkill {
    private String id;
    private String name;
    private String description;
    private String category;
    private boolean enabled;
    private int priority;
    private List<String> toolNames;
}