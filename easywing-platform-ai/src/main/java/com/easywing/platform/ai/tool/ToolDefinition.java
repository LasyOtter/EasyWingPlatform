package com.easywing.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;
    private String category;
    private JsonNode parametersSchema; // JSON Schema for parameters
    private AiTool executor;
    private boolean async;
    private long timeoutMs;
    private List<String> requiredPermissions;
    private Map<String, Object> metadata;

    /**
     * 创建简单的工具定义（从 AiTool 实例）
     */
    public static ToolDefinition fromTool(AiTool tool) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .category("builtin")
                .executor(tool)
                .async(false)
                .timeoutMs(30000)
                .build();

        // 如果工具提供了 JSON Schema，解析并设置
        if (tool.getParametersSchema() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                JsonNode schema = mapper.readTree(tool.getParametersSchema());
                definition.setParametersSchema(schema);
            } catch (Exception e) {
                // 忽略解析错误，不影响工具注册
            }
        }

        return definition;
    }
}
