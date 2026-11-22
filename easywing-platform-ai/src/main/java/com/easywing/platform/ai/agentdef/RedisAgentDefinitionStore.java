package com.easywing.platform.ai.agentdef;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Agent 定义的 Redis 存储。
 * <p>
 * 以单个 Hash（{@code ai:agent-defs}）保存全部条目，字段名为条目 ID，值为 JSON。
 * 多实例读取同一份数据，变更即时对全部实例可见。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.agent-def", name = "storage-type", havingValue = "redis")
public class RedisAgentDefinitionStore implements AgentDefinitionStore {

    private static final String AGENT_DEF_KEY = "ai:agent-defs";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAgentDefinitionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AgentDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return;
        }
        try {
            redisTemplate
                    .opsForHash()
                    .put(AGENT_DEF_KEY, definition.getId(), objectMapper.writeValueAsString(definition));
        } catch (Exception e) {
            log.error("Failed to persist agent definition: {}", definition.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        redisTemplate.opsForHash().delete(AGENT_DEF_KEY, id);
    }

    @Override
    public AgentDefinition findById(String id) {
        Object value = redisTemplate.opsForHash().get(AGENT_DEF_KEY, id);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.toString(), AgentDefinition.class);
        } catch (Exception e) {
            log.error("Failed to deserialize agent definition: {}", id, e);
            return null;
        }
    }

    @Override
    public List<AgentDefinition> listAll() {
        List<AgentDefinition> result = new ArrayList<>();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(AGENT_DEF_KEY);
        for (Object value : entries.values()) {
            try {
                result.add(objectMapper.readValue(value.toString(), AgentDefinition.class));
            } catch (Exception e) {
                log.error("Failed to deserialize agent definition entry", e);
            }
        }
        result.sort(Comparator.comparingLong(AgentDefinition::getUpdatedAt)
                .thenComparing(AgentDefinition::getId)
                .reversed());
        return result;
    }
}
