package com.easywing.platform.ai.workspace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Attached Workspaces 配置（PRD 4.2 实验性能力）。
 * <p>
 * 绑定 {@code easywing.ai.workspace.*}。该能力允许 Agent 在托管/个人工作区中查看目录树、
 * 读写文件并执行 Bash，属于高危能力，因此 {@code enabled} 与 {@code allowBash} 默认均为
 * {@code false}，必须由管理员显式开启；Bash 还要求调用方持有 {@link #adminScope} 声明的权限。
 * 工作区文件落盘在 {@link #baseDir}（未配置时取 {@code ~/easywing/workspaces}），
 * 路径解析做了归一化与目录穿越防护，文件大小/数量/树深度均有上限。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.ai.workspace")
public class WorkspaceProperties {

    /** 是否开启 Attached Workspaces 能力，默认关闭。 */
    private boolean enabled = false;

    /** 工作区元数据存储后端：memory（默认）或 redis（多实例共享）。 */
    private String storageType = "memory";

    /** 工作区文件根目录；留空时取 {@code ~/easywing/workspaces}。 */
    private String baseDir = "";

    /** 单文件大小上限（字节）。 */
    private long maxFileSize = 1024L * 1024L;

    /** 单个工作区文件数量上限。 */
    private int maxFilesPerWorkspace = 500;

    /** 目录树返回的最大深度。 */
    private int maxTreeDepth = 8;

    /** 单条路径的最大长度。 */
    private int maxPathLength = 4096;

    /** 单用户可创建的工作区数量上限。 */
    private int maxWorkspacesPerUser = 20;

    /** 单名/描述长度上限。 */
    private int maxNameLength = 128;

    private int maxDescriptionLength = 2000;

    /** 是否允许在工作区内执行 Bash，默认关闭，且需调用方具备 {@link #adminScope} 权限。 */
    private boolean allowBash = false;

    /** Bash 单次执行超时（毫秒）。 */
    private long bashTimeoutMs = 15000L;

    /** Bash 单侧输出（stdout/stderr）返回的最大字节数。 */
    private int bashMaxOutputSize = 64 * 1024;

    /** 授权执行 Bash 所需权限（默认 {@code workspace-management}）。 */
    private String adminScope = "workspace-management";
}
