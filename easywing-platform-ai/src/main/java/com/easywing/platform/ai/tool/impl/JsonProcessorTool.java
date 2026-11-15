package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * JSON 处理工具 - 解析、查询和转换 JSON 数据
 */
@Slf4j
@Component
public class JsonProcessorTool implements AiTool {

    private final ObjectMapper objectMapper;

    public JsonProcessorTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "json_processor";
    }

    @Override
    public String getDescription() {
        return "JSON 数据处理工具。" + "输入参数：operation (string) - 操作类型: 'parse', 'query', 'validate', 'format', 'merge'。"
                + "json (string) - JSON 字符串。"
                + "path (string, 可选) - JSONPath 表达式，用于 query 操作。"
                + "json2 (string, 可选) - 第二个 JSON 字符串，用于 merge 操作";
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String operation = (String) arguments.get("operation");
            String jsonStr = (String) arguments.get("json");

            if (operation == null) {
                throw new IllegalArgumentException("operation 参数不能为空");
            }

            log.info("Executing JSON operation: {}", operation);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", operation);

            try {
                Object result =
                        switch (operation.toLowerCase()) {
                            case "parse" -> {
                                JsonNode node = objectMapper.readTree(jsonStr);
                                yield objectMapper.convertValue(node, Map.class);
                            }
                            case "validate" -> {
                                try {
                                    objectMapper.readTree(jsonStr);
                                    yield true;
                                } catch (Exception e) {
                                    Map<String, Object> validationResult = new HashMap<>();
                                    validationResult.put("valid", false);
                                    validationResult.put("error", e.getMessage());
                                    yield validationResult;
                                }
                            }
                            case "format" -> {
                                JsonNode node = objectMapper.readTree(jsonStr);
                                yield objectMapper
                                        .writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(node);
                            }
                            case "query" -> {
                                String path = (String) arguments.get("path");
                                JsonNode node = objectMapper.readTree(jsonStr);
                                JsonNode result1 = node.at(path);
                                yield result1.isMissingNode() ? null : objectMapper.convertValue(result1, Object.class);
                            }
                            case "merge" -> {
                                String json2Str = (String) arguments.get("json2");
                                JsonNode node1 = objectMapper.readTree(jsonStr);
                                JsonNode node2 = objectMapper.readTree(json2Str);
                                if (node1.isObject() && node2.isObject()) {
                                    ObjectNode merged = ((ObjectNode) node1).deepCopy();
                                    merged.setAll((ObjectNode) node2);
                                    yield objectMapper.convertValue(merged, Map.class);
                                } else {
                                    throw new IllegalArgumentException("merge 操作仅支持 JSON 对象");
                                }
                            }
                            default -> throw new IllegalArgumentException("不支持的操作: " + operation);
                        };

                response.put("result", result);
                log.info("JSON operation completed: {}", operation);
                return response;
            } catch (Exception e) {
                log.error("JSON operation failed", e);
                response.put("success", false);
                response.put("error", e.getMessage());
                return response;
            }
        });
    }
}
