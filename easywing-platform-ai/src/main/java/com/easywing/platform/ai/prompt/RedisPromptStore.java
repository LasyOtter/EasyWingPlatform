package com.easywing.platform.ai.prompt;

import com.easywing.platform.ai.session.SessionOwnership;
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
 * Prompt 库 Redis 存储。
 * <p>
 * 以单个 Hash（{@code ai:prompts}）保存全部条目，字段名为条目 ID，值为 JSON。
 * 多实例读取同一份数据，变更即时对全部实例可见。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.prompt", name = "storage-type", havingValue = "redis")
public class RedisPromptStore implements PromptStore {

    private static final String PROMPT_KEY = "ai:prompts";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPromptStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(PromptDefinition prompt) {
        if (prompt == null || prompt.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(PROMPT_KEY, prompt.getId(), objectMapper.writeValueAsString(prompt));
        } catch (Exception e) {
            log.error("Failed to persist prompt: {}", prompt.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        redisTemplate.opsForHash().delete(PROMPT_KEY, id);
    }

    @Override
    public PromptDefinition findById(String id) {
        Object value = redisTemplate.opsForHash().get(PROMPT_KEY, id);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.toString(), PromptDefinition.class);
        } catch (Exception e) {
            log.error("Failed to deserialize prompt: {}", id, e);
            return null;
        }
    }

    @Override
    public List<PromptDefinition> listByUser(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<PromptDefinition> result = new ArrayList<>();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(PROMPT_KEY);
        for (Object value : entries.values()) {
            try {
                PromptDefinition prompt = objectMapper.readValue(value.toString(), PromptDefinition.class);
                if (prompt.getUserId() != null
                        && prompt.getUserId().equals(userId)
                        && SessionOwnership.normalizeTenant(prompt.getTenantId())
                                .equals(normalizedTenant)) {
                    result.add(prompt);
                }
            } catch (Exception e) {
                log.error("Failed to deserialize prompt entry", e);
            }
        }
        result.sort(Comparator.comparingLong(PromptDefinition::getUpdatedAt).reversed());
        return result;
    }
}
