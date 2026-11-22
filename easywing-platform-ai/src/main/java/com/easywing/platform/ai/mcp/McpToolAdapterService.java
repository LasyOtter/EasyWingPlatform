package com.easywing.platform.ai.mcp;

import com.easywing.platform.ai.tool.AiTool;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Discovers MCP tools and exposes them through the existing Agent tool registry.
 */
@Slf4j
@Service
public class McpToolAdapterService {

    private final McpClient mcpClient;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public McpToolAdapterService(McpClient mcpClient, ToolExecutor toolExecutor, ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public Mono<McpResponse> listToolsAndRegister(String serverName, McpServerConfig server) {
        server.setName(serverName);
        return mcpClient.listTools(server).doOnNext(response -> {
            if (response.getError() != null || response.getResult() == null) {
                return;
            }
            JsonNode tools = objectMapper.valueToTree(response.getResult()).path("tools");
            if (!tools.isArray()) {
                return;
            }
            tools.forEach(tool -> register(serverName, server, tool));
        });
    }

    private void register(String serverName, McpServerConfig server, JsonNode tool) {
        String remoteName = tool.path("name").asText();
        if (remoteName.isBlank()) {
            return;
        }
        String toolName = "mcp_" + normalize(serverName) + "_" + normalize(remoteName);
        String description = tool.path("description").asText("MCP tool " + remoteName);
        String schema = tool.path("inputSchema").isMissingNode()
                ? "{\"type\":\"object\"}"
                : tool.path("inputSchema").toString();
        toolExecutor.registerTool(
                new McpTool(toolName, description, schema, server, remoteName, mcpClient, objectMapper));
        log.info("Registered MCP tool {} from server {}", toolName, serverName);
    }

    private String normalize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static class McpTool implements AiTool {

        private final String name;
        private final String description;
        private final String schema;
        private final McpServerConfig server;
        private final String remoteName;
        private final McpClient mcpClient;
        private final ObjectMapper objectMapper;

        private McpTool(
                String name,
                String description,
                String schema,
                McpServerConfig server,
                String remoteName,
                McpClient mcpClient,
                ObjectMapper objectMapper) {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.server = server;
            this.remoteName = remoteName;
            this.mcpClient = mcpClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getParametersSchema() {
            return schema;
        }

        @Override
        public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
            return mcpClient.callTool(server, remoteName, arguments).flatMap(response -> {
                if (response.getError() != null) {
                    return Mono.error(
                            new IllegalStateException(response.getError().getMessage()));
                }
                Object rawResult = response.getResult();
                Map<String, Object> result;
                if (rawResult instanceof Map<?, ?> rawMap) {
                    result = new HashMap<>();
                    rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                } else if (rawResult == null) {
                    result = new HashMap<>();
                } else {
                    // MCP 工具可能返回数组或标量，统一包装为对象，避免 convertValue 抛异常
                    result = new HashMap<>();
                    result.put("value", rawResult);
                }
                result.put("success", !Boolean.TRUE.equals(result.get("isError")));
                return Mono.just(result);
            });
        }
    }
}
