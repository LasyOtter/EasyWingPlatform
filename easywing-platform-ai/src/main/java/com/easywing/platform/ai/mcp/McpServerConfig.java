package com.easywing.platform.ai.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 单个 MCP Server 的接入配置，支持 HTTP/SSE 与本地 stdio 两种传输方式。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    public static final String TYPE_HTTP = "http";
    public static final String TYPE_STDIO = "stdio";

    private static final String NAME_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}";

    /** 配置键名，由装配层写入，仅用于校验与日志。 */
    private String name;

    /** 传输方式：http（默认）或 stdio。 */
    @Builder.Default
    private String type = TYPE_HTTP;

    /** HTTP 传输的服务端点。 */
    private String url;

    /** HTTP Bearer Token，仅 http 传输使用，建议经环境变量注入。 */
    private String bearerToken;

    /** stdio 传输的可执行文件。 */
    private String command;

    /** stdio 传输的命令行参数。 */
    @Builder.Default
    private List<String> args = new ArrayList<>();

    /** stdio 子进程附加环境变量；值形如 ${ENV_VAR} 时从当前进程环境解析。 */
    @Builder.Default
    private Map<String, String> env = new HashMap<>();

    /** 是否启用，默认启用。 */
    @Builder.Default
    private boolean enabled = true;

    /** 允许访问该 Server 的租户列表，为空表示不限制。 */
    @Builder.Default
    private List<String> allowedTenants = new ArrayList<>();

    /** 允许访问该 Server 的角色列表，为空表示不限制。 */
    @Builder.Default
    private List<String> allowedRoles = new ArrayList<>();

    /** 初始化与请求超时时间（秒）。 */
    @Builder.Default
    private int timeoutSeconds = 30;

    public String effectiveType() {
        return type == null || type.isBlank() ? TYPE_HTTP : type.trim().toLowerCase();
    }

    public boolean isStdio() {
        return TYPE_STDIO.equals(effectiveType());
    }

    public boolean isHttp() {
        return TYPE_HTTP.equals(effectiveType());
    }

    public boolean hasAllowedTenants() {
        return allowedTenants != null && !allowedTenants.isEmpty();
    }

    public boolean hasAllowedRoles() {
        return allowedRoles != null && !allowedRoles.isEmpty();
    }

    /**
     * 校验配置完整性，非法配置直接抛出 IllegalArgumentException，避免运行期才失败。
     */
    public void validate(String serverName) {
        if (!StringUtils.hasText(serverName) || !serverName.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("非法 MCP server 名称: " + serverName);
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("MCP server " + serverName + " 的 timeoutSeconds 必须大于 0");
        }
        if (isHttp() && !StringUtils.hasText(url)) {
            throw new IllegalArgumentException("MCP server " + serverName + " 缺少 url 配置");
        }
        if (isStdio() && !StringUtils.hasText(command)) {
            throw new IllegalArgumentException("MCP server " + serverName + " 缺少 command 配置");
        }
    }
}
