package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 字符串操作工具 - 提供各种字符串处理功能
 */
@Slf4j
@Component
public class StringManipulationTool implements AiTool {

    @Override
    public String getName() {
        return "string_manipulation";
    }

    @Override
    public String getDescription() {
        return "字符串操作工具，支持多种操作。" +
                "输入参数：operation (string) - 操作类型: 'uppercase', 'lowercase', 'reverse', 'length', " +
                "'trim', 'replace', 'substring', 'contains', 'split', 'similarity'。" +
                "text (string) - 要处理的文本。" +
                "其他参数根据操作类型而定。";
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String operation = (String) arguments.get("operation");
            String text = (String) arguments.get("text");

            if (operation == null || text == null) {
                throw new IllegalArgumentException("operation 和 text 参数不能为空");
            }

            log.info("Executing string operation: {} on text length: {}", operation, text.length());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", operation);

            try {
                Object result = switch (operation.toLowerCase()) {
                    case "uppercase" -> text.toUpperCase();
                    case "lowercase" -> text.toLowerCase();
                    case "reverse" -> new StringBuilder(text).reverse().toString();
                    case "length" -> text.length();
                    case "trim" -> text.trim();
                    case "replace" -> {
                        String from = (String) arguments.get("from");
                        String to = (String) arguments.get("to");
                        yield text.replace(from, to);
                    }
                    case "substring" -> {
                        int start = ((Number) arguments.get("start")).intValue();
                        Integer end = arguments.containsKey("end") ?
                            ((Number) arguments.get("end")).intValue() : null;
                        yield end != null ? text.substring(start, end) : text.substring(start);
                    }
                    case "contains" -> {
                        String searchText = (String) arguments.get("search");
                        yield text.contains(searchText);
                    }
                    case "split" -> {
                        String delimiter = (String) arguments.getOrDefault("delimiter", ",");
                        yield text.split(delimiter);
                    }
                    case "similarity" -> {
                        String compareText = (String) arguments.get("compareText");
                        LevenshteinDistance levenshtein = new LevenshteinDistance();
                        int distance = levenshtein.apply(text, compareText);
                        int maxLen = Math.max(text.length(), compareText.length());
                        double similarity = maxLen == 0 ? 1.0 : 1.0 - ((double) distance / maxLen);
                        Map<String, Object> simResult = new HashMap<>();
                        simResult.put("distance", distance);
                        simResult.put("similarity", similarity);
                        yield simResult;
                    }
                    default -> throw new IllegalArgumentException("不支持的操作: " + operation);
                };

                response.put("result", result);
                log.info("String operation completed: {}", operation);
                return response;
            } catch (Exception e) {
                log.error("String operation failed", e);
                response.put("success", false);
                response.put("error", e.getMessage());
                return response;
            }
        });
    }
}
