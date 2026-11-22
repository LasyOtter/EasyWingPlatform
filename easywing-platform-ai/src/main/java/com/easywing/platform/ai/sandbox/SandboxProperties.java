package com.easywing.platform.ai.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代码沙箱配置（PRD 4.3）。
 * <p>
 * 绑定 {@code easywing.ai.sandbox.*}，用于「执行超时与资源配额可配置」。
 * 仅实现 {@code mode=process}（{@link ProcessBuilder} 子进程隔离）；{@code mode=docker}
 * 尚未实现，配置为该值时仅记录告警并按 process 模式运行。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.ai.sandbox")
public class SandboxProperties {

    /** 隔离模式，当前仅支持 process。 */
    private String mode = "process";

    /** 单次执行超时（毫秒）。 */
    private long maxExecutionTime = 10000;

    /** 声明内存上限（MB）；process 模式无强制手段，仅作记录。 */
    private int maxMemoryMB = 512;

    /** 单路输出（stdout/stderr）字节上限。 */
    private int maxOutputSize = 10240;

    /** 是否允许网络访问，默认关闭以满足 PRD 的网络隔离要求。 */
    private boolean allowNetworkAccess = false;

    /** 是否允许文件系统访问，默认关闭；开启后可跑含文件读写的示例。 */
    private boolean allowFileSystemAccess = false;

    /** 单次请求允许的上传文件数量上限。 */
    private int maxInputFiles = 8;

    /** 单个上传文件大小上限（字节）。 */
    private long maxInputFileSize = 1024 * 1024;

    /** 上传文件总大小上限（字节）。 */
    private long maxInputTotalBytes = 2 * 1024 * 1024;

    /** 单次执行返回的结果文件数量上限。 */
    private int maxOutputFiles = 16;

    /** 单个结果文件大小上限（字节），超出部分截断。 */
    private long maxOutputFileSize = 1024 * 1024;

    /** 结果文件总大小上限（字节）。 */
    private long maxOutputTotalBytes = 2 * 1024 * 1024;
}
