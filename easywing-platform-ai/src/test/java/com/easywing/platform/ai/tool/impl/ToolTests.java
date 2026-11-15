package com.easywing.platform.ai.tool.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 工具测试类
 */
class ToolTests {

    @Test
    void testCalculatorTool() {
        CalculatorTool tool = new CalculatorTool();

        Map<String, Object> args = new HashMap<>();
        args.put("expression", "2 + 2 * 3");

        Mono<Map<String, Object>> result = tool.execute(args);

        StepVerifier.create(result)
                .assertNext(res -> {
                    assertTrue((Boolean) res.get("success"));
                    assertEquals(8.0, ((Number) res.get("result")).doubleValue());
                })
                .verifyComplete();
    }

    @Test
    void testCurrentTimeTool() {
        CurrentTimeTool tool = new CurrentTimeTool();

        Map<String, Object> args = new HashMap<>();
        args.put("timezone", "Asia/Shanghai");

        Mono<Map<String, Object>> result = tool.execute(args);

        StepVerifier.create(result)
                .assertNext(res -> {
                    assertTrue((Boolean) res.get("success"));
                    assertNotNull(res.get("formatted"));
                    assertEquals("Asia/Shanghai", res.get("timezone"));
                })
                .verifyComplete();
    }

    @Test
    void testStringManipulationTool() {
        StringManipulationTool tool = new StringManipulationTool();

        Map<String, Object> args = new HashMap<>();
        args.put("text", "hello world");
        args.put("operation", "uppercase");

        Mono<Map<String, Object>> result = tool.execute(args);

        StepVerifier.create(result)
                .assertNext(res -> {
                    assertTrue((Boolean) res.get("success"));
                    assertEquals("HELLO WORLD", res.get("result"));
                })
                .verifyComplete();
    }

    @Test
    void testDataAnalysisTool() {
        DataAnalysisTool tool = new DataAnalysisTool();

        Map<String, Object> args = new HashMap<>();
        args.put("operation", "statistics");
        args.put("data", java.util.List.of(1, 2, 3, 4, 5));

        Mono<Map<String, Object>> result = tool.execute(args);

        StepVerifier.create(result)
                .assertNext(res -> {
                    assertTrue((Boolean) res.get("success"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stats = (Map<String, Object>) res.get("result");
                    assertEquals(5, stats.get("count"));
                    assertEquals(3.0, ((Number) stats.get("mean")).doubleValue());
                })
                .verifyComplete();
    }

    @Test
    void testJsonProcessorTool() {
        JsonProcessorTool tool = new JsonProcessorTool(new com.fasterxml.jackson.databind.ObjectMapper());

        Map<String, Object> args = new HashMap<>();
        args.put("operation", "validate");
        args.put("json", "{\"name\": \"test\", \"value\": 123}");

        Mono<Map<String, Object>> result = tool.execute(args);

        StepVerifier.create(result)
                .assertNext(res -> {
                    assertTrue((Boolean) res.get("success"));
                    assertEquals(true, res.get("result"));
                })
                .verifyComplete();
    }
}
