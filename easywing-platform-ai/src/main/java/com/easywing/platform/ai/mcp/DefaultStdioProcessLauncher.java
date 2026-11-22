package com.easywing.platform.ai.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认的 stdio 子进程启动器：按 command + args 拉起进程，并把配置中的环境变量合并进子进程。
 * 环境变量值形如 ${VAR} 时从当前进程环境解析，避免把密钥写进配置文件。
 */
@Slf4j
@Component
public class DefaultStdioProcessLauncher implements StdioProcessLauncher {

    @Override
    public Process start(McpServerConfig server) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(server.getCommand());
        if (server.getArgs() != null) {
            command.addAll(server.getArgs());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Map<String, String> environment = builder.environment();
        if (server.getEnv() != null) {
            server.getEnv().forEach((name, value) -> environment.put(name, resolve(value)));
        }
        return builder.start();
    }

    private String resolve(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 3 && trimmed.startsWith("${") && trimmed.endsWith("}")) {
            String name = trimmed.substring(2, trimmed.length() - 1);
            String resolved = System.getenv(name);
            if (resolved == null) {
                log.warn("MCP stdio 环境变量 {} 未设置，按字面量处理", name);
                return value;
            }
            return resolved;
        }
        return value;
    }
}
