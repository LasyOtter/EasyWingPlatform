package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 数据分析工具 - 对数值数据进行统计分析
 */
@Slf4j
@Component
public class DataAnalysisTool implements AiTool {

    @Override
    public String getName() {
        return "data_analysis";
    }

    @Override
    public String getDescription() {
        return "对数值数据进行统计分析。" + "输入参数：operation (string) - 操作类型: 'statistics', 'sort', 'filter', 'group'。"
                + "data (array) - 数值数组。"
                + "其他参数根据操作类型而定";
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String operation = (String) arguments.get("operation");
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) arguments.get("data");

            if (operation == null || data == null) {
                throw new IllegalArgumentException("operation 和 data 参数不能为空");
            }

            log.info("Executing data analysis operation: {} on {} items", operation, data.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", operation);
            response.put("dataSize", data.size());

            try {
                Object result =
                        switch (operation.toLowerCase()) {
                            case "statistics" -> calculateStatistics(data);
                            case "sort" -> {
                                String order = (String) arguments.getOrDefault("order", "asc");
                                yield sortData(data, order);
                            }
                            case "filter" -> {
                                String condition = (String) arguments.get("condition");
                                Double threshold = arguments.containsKey("threshold")
                                        ? ((Number) arguments.get("threshold")).doubleValue()
                                        : null;
                                yield filterData(data, condition, threshold);
                            }
                            case "group" -> {
                                Integer bins = arguments.containsKey("bins")
                                        ? ((Number) arguments.get("bins")).intValue()
                                        : 10;
                                yield groupData(data, bins);
                            }
                            default -> throw new IllegalArgumentException("不支持的操作: " + operation);
                        };

                response.put("result", result);
                log.info("Data analysis completed: {}", operation);
                return response;
            } catch (Exception e) {
                log.error("Data analysis failed", e);
                response.put("success", false);
                response.put("error", e.getMessage());
                return response;
            }
        });
    }

    private Map<String, Object> calculateStatistics(List<Object> data) {
        List<Double> numbers = data.stream()
                .map(obj -> {
                    if (obj instanceof Number) {
                        return ((Number) obj).doubleValue();
                    }
                    return Double.parseDouble(obj.toString());
                })
                .collect(Collectors.toList());

        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("数据为空");
        }

        Collections.sort(numbers);
        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / numbers.size();
        double median = numbers.size() % 2 == 0
                ? (numbers.get(numbers.size() / 2 - 1) + numbers.get(numbers.size() / 2)) / 2
                : numbers.get(numbers.size() / 2);
        double variance = numbers.stream()
                .mapToDouble(n -> Math.pow(n - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        Map<String, Object> stats = new HashMap<>();
        stats.put("count", numbers.size());
        stats.put("sum", sum);
        stats.put("mean", mean);
        stats.put("median", median);
        stats.put("min", numbers.get(0));
        stats.put("max", numbers.get(numbers.size() - 1));
        stats.put("variance", variance);
        stats.put("stdDev", stdDev);
        stats.put("range", numbers.get(numbers.size() - 1) - numbers.get(0));

        return stats;
    }

    private List<Double> sortData(List<Object> data, String order) {
        List<Double> numbers =
                data.stream().map(obj -> ((Number) obj).doubleValue()).collect(Collectors.toList());

        if ("desc".equalsIgnoreCase(order)) {
            numbers.sort(Collections.reverseOrder());
        } else {
            Collections.sort(numbers);
        }

        return numbers;
    }

    private List<Double> filterData(List<Object> data, String condition, Double threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("filter 操作需要 threshold 参数");
        }

        return data.stream()
                .map(obj -> ((Number) obj).doubleValue())
                .filter(n -> {
                    return switch (condition) {
                        case "gt" -> n > threshold;
                        case "gte" -> n >= threshold;
                        case "lt" -> n < threshold;
                        case "lte" -> n <= threshold;
                        case "eq" -> Math.abs(n - threshold) < 0.0001;
                        default -> throw new IllegalArgumentException("不支持的条件: " + condition);
                    };
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> groupData(List<Object> data, int bins) {
        List<Double> numbers =
                data.stream().map(obj -> ((Number) obj).doubleValue()).collect(Collectors.toList());

        double min = Collections.min(numbers);
        double max = Collections.max(numbers);
        double binWidth = (max - min) / bins;

        Map<String, Integer> histogram = new LinkedHashMap<>();
        for (int i = 0; i < bins; i++) {
            final int binIndex = i;
            double binStart = min + i * binWidth;
            double binEnd = binStart + binWidth;
            String binLabel = String.format("[%.2f, %.2f)", binStart, binEnd);

            long count = numbers.stream()
                    .filter(n -> n >= binStart && (binIndex == bins - 1 ? n <= binEnd : n < binEnd))
                    .count();

            histogram.put(binLabel, (int) count);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bins", bins);
        result.put("binWidth", binWidth);
        result.put("histogram", histogram);
        return result;
    }
}
