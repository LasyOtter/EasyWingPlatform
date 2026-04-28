package com.easywing.platform.ai.mcp;

import com.easywing.platform.ai.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpServer {

    private final ToolExecutor toolExecutor;

    public McpServer(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public Mono<McpResponse> handleRequest(McpRequest request) {
        String method = request.getMethod();
        String id = request.getId();

        switch (method) {
            case McpRequest.METHOD_INITIALIZE:
                return Mono.just(McpResponse.success(Map.of("version", "1.0"), id));
            case McpRequest.METHOD_TOOLS_LIST:
                return Mono.just(McpResponse.success(
                        Map.of("tools", toolExecutor.getAvailableTools()), id));
            default:
                return Mono.just(McpResponse.error(-32601, "Method not found: " + method, id));
        }
    }
}