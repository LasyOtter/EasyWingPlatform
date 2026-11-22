package com.easywing.platform.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.tool.AiTool;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class McpToolAdapterServiceTest {

    @Test
    void registersDiscoveredMcpToolsForAgentExecution() {
        McpClient client = org.mockito.Mockito.mock(McpClient.class);
        ToolExecutor executor = org.mockito.Mockito.mock(ToolExecutor.class);
        McpToolAdapterService service = new McpToolAdapterService(client, executor, new ObjectMapper());
        when(client.listTools(any(McpServerConfig.class)))
                .thenReturn(Mono.just(McpResponse.success(
                        Map.of(
                                "tools",
                                List.of(Map.of(
                                        "name", "search",
                                        "description", "Search internal data",
                                        "inputSchema",
                                                Map.of(
                                                        "type",
                                                        "object",
                                                        "properties",
                                                        Map.of("query", Map.of("type", "string")))))),
                        "list")));
        when(client.callTool(any(McpServerConfig.class), eq("search"), any()))
                .thenReturn(Mono.just(McpResponse.success(Map.of("content", List.of("result")), "call")));

        McpServerConfig server = McpServerConfig.builder()
                .name("internal")
                .type(McpServerConfig.TYPE_HTTP)
                .url("http://mcp.local")
                .build();
        service.listToolsAndRegister("internal", server).block();

        ArgumentCaptor<AiTool> toolCaptor = ArgumentCaptor.forClass(AiTool.class);
        verify(executor).registerTool(toolCaptor.capture());
        AiTool tool = toolCaptor.getValue();
        assertThat(tool.getName()).isEqualTo("mcp_internal_search");
        assertThat(tool.getParametersSchema()).contains("\"query\"");
        assertThat(tool.execute(Map.of("query", "test")).block())
                .containsEntry("success", true)
                .containsKey("content");
    }
}
