package com.easywing.platform.ai.agentdef;

import java.util.List;

/**
 * Agent 定义的持久化抽象。
 * <p>
 * 实现需保证多实例下的数据共享：内存实现仅用于单实例，多实例应使用 Redis 实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AgentDefinitionStore {

    void save(AgentDefinition definition);

    void delete(String id);

    AgentDefinition findById(String id);

    List<AgentDefinition> listAll();
}
