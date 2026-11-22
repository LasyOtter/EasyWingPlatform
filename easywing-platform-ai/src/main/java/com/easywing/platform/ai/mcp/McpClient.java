package com.easywing.platform.ai.mcp;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * MCP 客户端门面，按 Server 配置的传输方式路由到具体 {@link McpTransport} 实现。
 */
@Slf4j
@Component
public class McpClient {

    private final McpTransportRegistry transportRegistry;

    public McpClient(McpTransportRegistry transportRegistry) {
        this.transportRegistry = transportRegistry;
    }

    /** 建立连接并完成 initialize 能力协商。 */
    public Mono<Boolean> connect(McpServerConfig server) {
        server.validate(server.getName());
        return transportRegistry.forServer(server).initialize(server);
    }

    public Mono<McpResponse> listTools(McpServerConfig server) {
        return send(
                server,
                McpRequest.builder()
                        .jsonrpc(McpRequest.JSONRPC_VERSION)
                        .id(UUID.randomUUID().toString())
                        .method(McpRequest.METHOD_TOOLS_LIST)
                        .params(Map.of())
                        .build());
    }

    public Mono<McpResponse> callTool(McpServerConfig server, String toolName, Map<String, Object> arguments) {
        return send(
                server,
                McpRequest.builder()
                        .jsonrpc(McpRequest.JSONRPC_VERSION)
                        .id(UUID.randomUUID().toString())
                        .method(McpRequest.METHOD_TOOLS_CALL)
                        .params(Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments))
                        .build());
    }

    public Mono<McpResponse> sendRequest(McpServerConfig server, McpRequest request) {
        return send(server, request);
    }

    public void close(McpServerConfig server) {
        transportRegistry.forServer(server).close(server);
    }

    private Mono<McpResponse> send(McpServerConfig server, McpRequest request) {
        server.validate(server.getName());
        return transportRegistry.forServer(server).send(server, request, false);
    }
}
