package com.easywing.platform.ai.agentdef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agent 定义的内存存储（默认实现）。
 * <p>
 * 单实例生效，重启或横向扩容会丢失/不一致；生产环境应配置 {@code storage-type=redis}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.agent-def",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryAgentDefinitionStore implements AgentDefinitionStore {

    private final Map<String, AgentDefinition> definitions = new ConcurrentHashMap<>();

    public InMemoryAgentDefinitionStore() {
        log.warn("Agent 定义使用内存存储：重启或横向扩容会丢失数据，生产环境请配置 easywing.ai.agent-def.storage-type=redis");
    }

    @Override
    public void save(AgentDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return;
        }
        definitions.put(definition.getId(), definition);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            definitions.remove(id);
        }
    }

    @Override
    public AgentDefinition findById(String id) {
        return id == null ? null : definitions.get(id);
    }

    @Override
    public List<AgentDefinition> listAll() {
        List<AgentDefinition> result = new ArrayList<>(definitions.values());
        result.sort(Comparator.comparingLong(AgentDefinition::getUpdatedAt)
                .thenComparing(AgentDefinition::getId)
                .reversed());
        return result;
    }
}
