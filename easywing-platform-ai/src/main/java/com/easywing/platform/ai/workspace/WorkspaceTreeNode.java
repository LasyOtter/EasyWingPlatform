package com.easywing.platform.ai.workspace;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作区目录树节点。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceTreeNode {

    private String name;

    /** 相对工作区根目录的路径，根节点为空串。 */
    private String path;

    /** {@code DIRECTORY} 或 {@code FILE}。 */
    private String type;

    private long size;

    @Builder.Default
    private List<WorkspaceTreeNode> children = new ArrayList<>();
}
