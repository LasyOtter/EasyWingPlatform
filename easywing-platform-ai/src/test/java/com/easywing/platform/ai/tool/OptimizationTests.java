package com.easywing.platform.ai.tool;

import static org.junit.jupiter.api.Assertions.*;

import com.easywing.platform.ai.tool.impl.CalculatorTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * 优化功能测试 - 缓存和参数验证
 */
class OptimizationTests {

    private ToolExecutor toolExecutor;
    private ToolExecutionCache cache;
    private ToolParameterValidator validator;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        registry = new ToolRegistry();
        validator = new ToolParameterValidator(objectMapper);
        // 不注入缓存：直接测试 ToolExecutor 执行路径
        toolExecutor = new ToolExecutor(registry, validator, cacheProviderReturningNull());
        cache = new ToolExecutionCache(toolExecutor);

        // 注册测试工具
        CalculatorTool calculatorTool = new CalculatorTool();
        toolExecutor.registerTool(calculatorTool);
    }

    /**
     * 返回不携带缓存的 ObjectProvider，使 ToolExecutor 走直接执行路径。
     */
    private static org.springframework.beans.factory.ObjectProvider<ToolExecutionCache> cacheProviderReturningNull() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public ToolExecutionCache getObject(Object... args) {
                return null;
            }

            @Override
            public ToolExecutionCache getIfAvailable() {
                return null;
            }

            @Override
            public ToolExecutionCache getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void testCacheHit() {
        Map<String, Object> args = Map.of("expression", "2+2");

        // 第一次调用 - 缓存未命中
        StepVerifier.create(cache.executeCached("calculator", args))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertNull(result.get("fromCache"));
                })
                .verifyComplete();

        // 第二次调用 - 缓存命中
        StepVerifier.create(cache.executeCached("calculator", args))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertTrue((Boolean) result.get("fromCache"));
                })
                .verifyComplete();
    }

    @Test
    void testCacheStats() {
        Map<String, Object> args = Map.of("expression", "2+2");

        // 执行几次调用
        cache.executeCached("calculator", args).block();
        cache.executeCached("calculator", args).block();
        cache.executeCached("calculator", Map.of("expression", "3+3")).block();

        Map<String, Object> stats = cache.getCacheStats();

        assertNotNull(stats);
        assertTrue((Long) stats.get("hitCount") >= 1);
        assertTrue((Long) stats.get("missCount") >= 2);
    }

    @Test
    void testCacheClear() {
        Map<String, Object> args = Map.of("expression", "2+2");

        // 缓存一个结果
        cache.executeCached("calculator", args).block();

        // 清空缓存
        cache.clearCache();

        // 再次调用应该是缓存未命中
        StepVerifier.create(cache.executeCached("calculator", args))
                .assertNext(result -> {
                    assertNull(result.get("fromCache"));
                })
                .verifyComplete();
    }

    @Test
    void testCacheHitDoesNotMutateCachedEntry() {
        Map<String, Object> args = Map.of("expression", "2+2");

        // 首次：未命中，不应带 fromCache
        Map<String, Object> first = cache.executeCached("calculator", args).block();
        assertNull(first.get("fromCache"));

        // 命中：返回副本并带 fromCache
        Map<String, Object> second = cache.executeCached("calculator", args).block();
        assertEquals(true, second.get("fromCache"));

        // 再次命中：缓存内对象不得被上一次写入的 fromCache 污染
        Map<String, Object> third = cache.executeCached("calculator", args).block();
        assertEquals(true, third.get("fromCache"));
        assertTrue((Long) cache.getCacheStats().get("hitCount") >= 2);
    }

    @Test
    void testNonCacheableToolIsNotCached() {
        ToolDefinition def = new ToolDefinition();
        def.setName("current_time");
        def.setDescription("time");
        def.setParametersSchema(new ObjectMapper().createObjectNode());
        def.setExecutor(new com.easywing.platform.ai.tool.impl.CurrentTimeTool());
        registry.register(def);

        cache.executeCached("current_time", Map.of()).block();
        cache.executeCached("current_time", Map.of()).block();

        // current_time 结果不可缓存，两次都不应命中
        assertEquals(0L, cache.getCacheStats().get("hitCount"));
    }

    @Test
    void testParameterValidation_Valid() {
        ToolDefinition tool = registry.get("calculator");
        Map<String, Object> validArgs = Map.of("expression", "2+2");

        ToolParameterValidator.ValidationResult result = validator.validate(tool, validArgs);

        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testParameterValidation_MissingRequired() {
        ToolDefinition tool = registry.get("calculator");
        Map<String, Object> invalidArgs = new HashMap<>();

        ToolParameterValidator.ValidationResult result = validator.validate(tool, invalidArgs);

        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("expression"));
    }

    @Test
    void testParameterValidation_WrongType() {
        ToolDefinition tool = registry.get("calculator");
        Map<String, Object> invalidArgs = Map.of("expression", 123); // 应该是 string

        ToolParameterValidator.ValidationResult result = validator.validate(tool, invalidArgs);

        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testToolExecutionWithValidation() {
        // 正确参数
        StepVerifier.create(toolExecutor.executeTool("calculator", Map.of("expression", "2+2")))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                })
                .verifyComplete();

        // 错误参数
        StepVerifier.create(toolExecutor.executeTool("calculator", Map.of()))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(result.get("error").toString().contains("参数验证失败"));
                })
                .verifyComplete();
    }
}
