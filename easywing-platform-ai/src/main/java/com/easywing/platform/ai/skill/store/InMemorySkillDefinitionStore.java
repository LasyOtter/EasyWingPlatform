package com.easywing.platform.ai.skill.store;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 技能定义内存存储
 * <p>
 * 当 {@code easywing.ai.skill.storage-type} 不是 {@code redis} 时生效，
 * 供本地开发与无 Redis 环境使用。重启丢失，不适用于集群部署。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.skill",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemorySkillDefinitionStore implements SkillDefinitionStore {

    private final ConcurrentHashMap<String, SkillDefinition> definitions = new ConcurrentHashMap<>();

    public InMemorySkillDefinitionStore() {
        log.warn("AI 技能目录使用内存存储（easywing.ai.skill.storage-type=memory）：" + "重启即丢失，集群下不共享，禁止用于生产");
    }

    @Override
    public List<SkillDefinition> loadAll() {
        return List.copyOf(definitions.values());
    }

    @Override
    public void save(SkillDefinition definition) {
        if (definition != null && definition.getName() != null) {
            definitions.put(definition.getName(), definition);
        }
    }

    @Override
    public void delete(String name) {
        definitions.remove(name);
    }
}
