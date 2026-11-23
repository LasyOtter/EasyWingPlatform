package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties.CustomEndpointConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom Endpoint 的 Redis 存储。
 * <p>
 * 端点配置以 JSON 形式写入 Hash {@code ai:custom-endpoints}，供多实例共享并在重启后恢复。
 * 存储失败只记录日志、不抛出，避免影响主流程；读取时跳过无法反序列化的条目。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.custom-endpoint-store", name = "storage-type", havingValue = "redis")
public class RedisCustomEndpointStore implements CustomEndpointStore {

    private static final String KEY = "ai:custom-endpoints";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCustomEndpointStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, CustomEndpointConfig> loadAll() {
        Map<String, CustomEndpointConfig> result = new LinkedHashMap<>();
        Map<Object, Object> entries = redis.opsForHash().entries(KEY);
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (!(entry.getValue() instanceof String json)) {
                continue;
            }
            try {
                result.put(String.valueOf(entry.getKey()), objectMapper.readValue(json, CustomEndpointConfig.class));
            } catch (JsonProcessingException e) {
                log.error("反序列化 Custom Endpoint 失败, id={}: {}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    @Override
    public void save(String id, CustomEndpointConfig config) {
        if (id == null || config == null) {
            return;
        }
        try {
            redis.opsForHash().put(KEY, id, objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            log.error("序列化 Custom Endpoint 失败, id={}: {}", id, e.getMessage());
        }
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            redis.opsForHash().delete(KEY, id);
        }
    }
}
