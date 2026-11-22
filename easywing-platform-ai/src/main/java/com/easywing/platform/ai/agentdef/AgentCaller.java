package com.easywing.platform.ai.agentdef;

import java.util.List;

/**
 * Agent 定义的调用方上下文：用户 ID、租户、所属群组。
 *
 * @param userId   用户 ID（JWT subject）
 * @param tenantId 租户 ID（未指定时为 {@code default}）
 * @param groups   所属群组/角色（用于按群组共享）
 * @author EasyWing Team
 * @since 1.0.0
 */
public record AgentCaller(String userId, String tenantId, List<String> groups) {}
