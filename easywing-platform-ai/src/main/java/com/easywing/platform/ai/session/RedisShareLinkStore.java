package com.easywing.platform.ai.session;

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
 * 共享链接的 Redis 存储，多实例共享同一份共享目录。
 * <p>
 * 以单个 Hash（{@code ai:share-links}）保存全部条目，字段名为共享令牌，值为 JSON。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.share", name = "storage-type", havingValue = "redis")
public class RedisShareLinkStore implements ShareLinkStore {

    private static final String SHARE_KEY = "ai:share-links";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisShareLinkStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ShareLink link) {
        if (link == null || link.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(SHARE_KEY, link.getId(), objectMapper.writeValueAsString(link));
        } catch (Exception e) {
            log.error("Failed to persist share link: {}", link.getId(), e);
        }
    }

    @Override
    public ShareLink findById(String id) {
        if (id == null) {
            return null;
        }
        Object value = redisTemplate.opsForHash().get(SHARE_KEY, id);
        return deserialize(value);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            redisTemplate.opsForHash().delete(SHARE_KEY, id);
        }
    }

    @Override
    public List<ShareLink> listBySession(String sessionId) {
        List<ShareLink> result = new ArrayList<>();
        if (sessionId == null) {
            return result;
        }
        for (ShareLink link : entries()) {
            if (sessionId.equals(link.getSessionId())) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparingLong(ShareLink::getCreatedAt).reversed());
        return result;
    }

    @Override
    public List<ShareLink> listByUser(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<ShareLink> result = new ArrayList<>();
        for (ShareLink link : entries()) {
            if (userId != null
                    && userId.equals(link.getUserId())
                    && SessionOwnership.normalizeTenant(link.getTenantId()).equals(normalizedTenant)) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparingLong(ShareLink::getCreatedAt).reversed());
        return result;
    }

    private List<ShareLink> entries() {
        List<ShareLink> result = new ArrayList<>();
        Map<Object, Object> values = redisTemplate.opsForHash().entries(SHARE_KEY);
        for (Object value : values.values()) {
            ShareLink link = deserialize(value);
            if (link != null) {
                result.add(link);
            }
        }
        return result;
    }

    private ShareLink deserialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.toString(), ShareLink.class);
        } catch (Exception e) {
            log.error("Failed to deserialize share link entry", e);
            return null;
        }
    }
}
