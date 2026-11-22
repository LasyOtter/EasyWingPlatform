package com.easywing.platform.ai.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作区元数据：归某用户 + 租户所有，文件落在 {@code <baseDir>/<id>}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {

    private String id;

    private String name;

    private String description;

    /** {@link WorkspaceType} 名称。 */
    @Builder.Default
    private String type = WorkspaceType.PERSONAL.name();

    private String ownerId;

    private String tenantId;

    private long createdAt;

    private long updatedAt;
}
