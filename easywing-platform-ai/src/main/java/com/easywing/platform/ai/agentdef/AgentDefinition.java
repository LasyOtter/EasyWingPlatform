package com.easywing.platform.ai.agentdef;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 定义：可复用的 Agent 配置模板（名称、系统提示词、模型、工具、技能、可见性）。
 * <p>
 * 归属维度为「租户 + 所有者」；共享关系记录在 {@code sharedWithUsers} / {@code sharedWithGroups}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition {

    private String id;

    private String name;

    private String description;

    private String systemPrompt;

    private String model;

    @Builder.Default
    private List<String> tools = new ArrayList<>();

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private String visibility = "PRIVATE";

    private String ownerId;

    private String tenantId;

    @Builder.Default
    private List<String> sharedWithUsers = new ArrayList<>();

    @Builder.Default
    private List<String> sharedWithGroups = new ArrayList<>();

    private long createdAt;

    private long updatedAt;
}
