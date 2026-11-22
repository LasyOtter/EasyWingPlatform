package com.easywing.platform.ai.workspace;

import com.easywing.platform.ai.session.SessionOwnership;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工作区内存存储。
 * <p>
 * 当 {@code easywing.ai.workspace.storage-type} 不是 {@code redis} 时生效，仅本实例可见，
 * 重启即丢失；文件本身仍落盘，但元数据丢失后文件目录不再可达。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.workspace",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryWorkspaceStore implements WorkspaceStore {

    private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();

    public InMemoryWorkspaceStore() {
        log.warn("AI 工作区使用内存存储（easywing.ai.workspace.storage-type=memory）：重启即丢失，集群下不共享，禁止用于生产");
    }

    @Override
    public void save(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }
        workspaces.put(workspace.getId(), workspace);
    }

    @Override
    public void delete(String id) {
        workspaces.remove(id);
    }

    @Override
    public Workspace findById(String id) {
        return workspaces.get(id);
    }

    @Override
    public List<Workspace> listAll() {
        return new ArrayList<>(workspaces.values());
    }

    @Override
    public List<Workspace> listByOwner(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<Workspace> result = new ArrayList<>();
        for (Workspace workspace : workspaces.values()) {
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
        return workspaces.values().stream()
                .filter(workspace -> workspace.getOwnerId() != null
                        && workspace.getOwnerId().equals(userId)
                        && SessionOwnership.normalizeTenant(workspace.getTenantId())
                                .equals(normalizedTenant))
                .count();
    }
}
