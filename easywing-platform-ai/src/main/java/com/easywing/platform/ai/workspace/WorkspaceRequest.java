package com.easywing.platform.ai.workspace;

import lombok.Data;

/**
 * 工作区创建/更新请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class WorkspaceRequest {

    private String name;

    private String description;

    /** {@code PERSONAL} 或 {@code MANAGED}；缺省为 {@code PERSONAL}。 */
    private String type;
}
