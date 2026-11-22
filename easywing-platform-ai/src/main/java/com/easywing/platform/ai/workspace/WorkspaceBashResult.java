package com.easywing.platform.ai.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作区 Bash 执行结果。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceBashResult {

    private int exitCode;

    private String stdout;

    private String stderr;

    /** 是否因超时被强制终止。 */
    private boolean timedOut;
}
