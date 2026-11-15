package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Function Calling 格式转换器
 */
public class FunctionCallingConverter {

    private final ObjectMapper objectMapper;

    public FunctionCallingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将工具定义转换为 OpenAI Function 格式
     */
    public List<FunctionDefinition> convertToFunctions(List<ToolDefinition> tools) {
        return tools.stream().map(this::convertToFunction).collect(Collectors.toList());
    }

    /**
     * 转换单个工具
     */
    public FunctionDefinition convertToFunction(ToolDefinition tool) {
        return FunctionDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .parameters(tool.getParametersSchema() != null ? tool.getParametersSchema() : createDefaultSchema())
                .build();
    }

    /**
     * 创建默认的参数 Schema
     */
    private JsonNode createDefaultSchema() {
        try {
            return objectMapper.readTree(
                    """
                    {
                      "type": "object",
                      "properties": {}
                    }
                    """);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * OpenAI Function 定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinition {
        private String name;
        private String description;
        private JsonNode parameters;
    }

    /**
     * Function Call 结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments; // JSON string
    }

    /**
     * 解析 Function Call 参数
     */
    public Map<String, Object> parseFunctionArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
