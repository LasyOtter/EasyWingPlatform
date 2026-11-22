package com.easywing.platform.ai.workspace;

import lombok.Data;

/**
 * 工作区文件写入请求：正文为 UTF-8 文本。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class WorkspaceFileWriteRequest {

    private String content;
}
