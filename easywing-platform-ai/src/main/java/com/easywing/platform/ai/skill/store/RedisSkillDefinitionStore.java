package com.easywing.platform.ai.skill.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 技能定义 Redis 存储
 * <p>
 * 以单个 Hash（{@code ai:skills}）保存全部技能定义，字段名为技能名，值为 JSON。
 * 多实例读取同一份数据，管理员变更即时对全部实例可见。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.skill", name = "storage-type", havingValue = "redis")
public class RedisSkillDefinitionStore implements SkillDefinitionStore {

    private static final String SKILL_KEY = "ai:skills";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSkillDefinitionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SkillDefinition> loadAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(SKILL_KEY);
        List<SkillDefinition> definitions = new ArrayList<>(entries.size());
        for (Object value : entries.values()) {
            try {
                definitions.add(objectMapper.readValue(value.toString(), SkillDefinition.class));
            } catch (Exception e) {
                log.error("Failed to deserialize skill definition", e);
            }
        }
        return definitions;
    }

    @Override
    public void save(SkillDefinition definition) {
        if (definition == null || definition.getName() == null) {
            return;
        }
        try {
            redisTemplate
                    .opsForHash()
                    .put(SKILL_KEY, definition.getName(), objectMapper.writeValueAsString(definition));
        } catch (Exception e) {
            log.error("Failed to persist skill definition: {}", definition.getName(), e);
        }
    }

    @Override
    public void delete(String name) {
        redisTemplate.opsForHash().delete(SKILL_KEY, name);
    }
}
