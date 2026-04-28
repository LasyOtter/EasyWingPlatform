package com.easywing.platform.ai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolExecutor {

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    public Mono<Map<String, Object>> executeTool(String toolName, Map<String, Object> arguments) {
        log.info("Executing tool: {}", toolName);
        return Mono.just(Map.of("success", true, "result", "Tool execution placeholder"));
    }

    public void registerTool(AiTool tool) {
        registry.register(ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .build());
        log.info("Registered tool: {}", tool.getName());
    }

    public List<ToolDefinition> getAvailableTools() {
        return registry.getAll();
    }
}