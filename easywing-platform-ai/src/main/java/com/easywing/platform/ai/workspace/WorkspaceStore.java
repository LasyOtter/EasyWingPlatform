package com.easywing.platform.ai.workspace;

import java.util.List;

/**
 * 工作区元数据存储。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface WorkspaceStore {

    void save(Workspace workspace);

    void delete(String id);

    Workspace findById(String id);

    List<Workspace> listAll();

    /** 按所有者 + 租户列举（跨租户隔离）。 */
    List<Workspace> listByOwner(String userId, String tenantId);

    /** 统计所有者 + 租户下的工作区数量。 */
    long countByOwner(String userId, String tenantId);
}
