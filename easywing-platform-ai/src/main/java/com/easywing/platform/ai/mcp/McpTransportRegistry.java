package com.easywing.platform.ai.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 按 {@link McpServerConfig#getType()} 选择 MCP 传输实现。
 */
@Component
public class McpTransportRegistry {

    private final Map<String, McpTransport> transports = new HashMap<>();

    public McpTransportRegistry(List<McpTransport> transportList) {
        transportList.forEach(transport -> transports.put(transport.type(), transport));
    }

    public McpTransport forServer(McpServerConfig server) {
        McpTransport transport = transports.get(server.effectiveType());
        if (transport == null) {
            throw new IllegalStateException("不支持的 MCP 传输方式: " + server.effectiveType());
        }
        return transport;
    }
}
