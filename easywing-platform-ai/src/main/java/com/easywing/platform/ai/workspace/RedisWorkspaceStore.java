package com.easywing.platform.ai.workspace;

import com.easywing.platform.ai.session.SessionOwnership;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 工作区 Redis 存储。
 * <p>
 * 以单个 Hash（{@code ai:workspaces}）保存全部条目，字段名为工作区 ID，值为 JSON，
 * 多实例共享同一份元数据。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.workspace", name = "storage-type", havingValue = "redis")
public class RedisWorkspaceStore implements WorkspaceStore {

    private static final String WORKSPACE_KEY = "ai:workspaces";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisWorkspaceStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }
        try {
            redisTemplate
                    .opsForHash()
                    .put(WORKSPACE_KEY, workspace.getId(), objectMapper.writeValueAsString(workspace));
        } catch (Exception e) {
            log.error("Failed to persist workspace: {}", workspace.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        redisTemplate.opsForHash().delete(WORKSPACE_KEY, id);
    }

    @Override
    public Workspace findById(String id) {
        Object value = redisTemplate.opsForHash().get(WORKSPACE_KEY, id);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.toString(), Workspace.class);
        } catch (Exception e) {
            log.error("Failed to deserialize workspace: {}", id, e);
            return null;
        }
    }

    @Override
    public List<Workspace> listAll() {
        List<Workspace> result = new ArrayList<>();
        for (Object value : redisTemplate.opsForHash().entries(WORKSPACE_KEY).values()) {
            Workspace workspace = deserialize(value);
            if (workspace != null) {
                result.add(workspace);
            }
        }
        return result;
    }

    @Override
    public List<Workspace> listByOwner(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<Workspace> result = new ArrayList<>();
        for (Workspace workspace : listAll()) {
            if (workspace.getOwnerId() != null
                    && workspace.getOwnerId().equals(userId)
                    && SessionOwnership.normalizeTenant(workspace.getTenantId()).equals(normalizedTenant)) {
                result.add(workspace);
            }
        }
        result.sort(Comparator.comparingLong(Workspace::getUpdatedAt).reversed());
        return result;
    }

    @Override
    public long countByOwner(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        return listAll().stream()
                .filter(workspace -> workspace.getOwnerId() != null
                        && workspace.getOwnerId().equals(userId)
                        && SessionOwnership.normalizeTenant(workspace.getTenantId())
                                .equals(normalizedTenant))
                .count();
    }

    private Workspace deserialize(Object value) {
        try {
            return objectMapper.readValue(value.toString(), Workspace.class);
        } catch (Exception e) {
            log.error("Failed to deserialize workspace entry", e);
            return null;
        }
    }
}
