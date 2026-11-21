package com.easywing.platform.ai.tool;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ToolExecutor {

    private final ToolRegistry registry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ToolParameterValidator validator;

    /**
     * 可选的结果缓存：存在时 {@link #executeTool(String, Map)} 走缓存。
     * 通过 ObjectProvider 注入，避免 ToolExecutor 与 ToolExecutionCache 循环依赖。
     */
    private final org.springframework.beans.factory.ObjectProvider<ToolExecutionCache> cacheProvider;

    public ToolExecutor(
            ToolRegistry registry,
            ToolParameterValidator validator,
            org.springframework.beans.factory.ObjectProvider<ToolExecutionCache> cacheProvider) {
        this.registry = registry;
        this.validator = validator;
        this.cacheProvider = cacheProvider;
        // 配置熔断器
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        this.circuitBreaker = CircuitBreaker.of("toolExecutor", circuitBreakerConfig);

        // 配置超时限制
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .build();
        this.timeLimiter = TimeLimiter.of("toolExecutor", timeLimiterConfig);
    }

    /**
     * 执行工具。
     * <p>
     * 存在 {@link ToolExecutionCache} 时走缓存路径，Agent 与 REST 共用同一入口，
     * 避免同一工具在两条链路上行为不一致。
     */
    public Mono<Map<String, Object>> executeTool(String toolName, Map<String, Object> arguments) {
        ToolExecutionCache cache = cacheProvider == null ? null : cacheProvider.getIfAvailable();
        if (cache != null) {
            return cache.executeCached(toolName, arguments);
        }
        return executeToolDirectly(toolName, arguments);
    }

    /**
     * 直接执行工具（不经缓存）
     */
    public Mono<Map<String, Object>> executeToolDirectly(String toolName, Map<String, Object> arguments) {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);

        ToolDefinition toolDef = registry.get(toolName);
        if (toolDef == null) {
            log.error("Tool not found: {}", toolName);
            return Mono.just(createErrorResponse(toolName, "工具未找到: " + toolName));
        }

        AiTool tool = toolDef.getExecutor();
        if (tool == null) {
            log.error("Tool executor not configured: {}", toolName);
            return Mono.just(createErrorResponse(toolName, "工具执行器未配置"));
        }

        // 验证参数
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        // JSON Schema 参数验证
        ToolParameterValidator.ValidationResult validationResult = validator.validate(toolDef, arguments);
        if (!validationResult.isValid()) {
            log.warn("Tool {} parameter validation failed: {}", toolName, validationResult.getErrorMessage());
            return Mono.just(createErrorResponse(toolName, "参数验证失败: " + validationResult.getErrorMessage()));
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> finalArguments = arguments;

        return tool.execute(arguments)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .doOnSuccess(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.info("Tool {} executed successfully in {}ms", toolName, executionTime);
                    result.put("executionTime", executionTime);
                    result.put("toolName", toolName);
                })
                .doOnError(e -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.error("Tool {} execution failed after {}ms", toolName, executionTime, e);
                })
                .onErrorResume(e -> {
                    String errorMessage = e.getMessage();
                    if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
                        errorMessage = "工具熔断器打开，请稍后重试";
                    } else if (e instanceof java.util.concurrent.TimeoutException) {
                        errorMessage = "工具执行超时";
                    }
                    return Mono.just(createErrorResponse(toolName, errorMessage));
                });
    }

    /**
     * 注册工具
     */
    public void registerTool(AiTool tool) {
        ToolDefinition definition = ToolDefinition.fromTool(tool);
        registry.register(definition);
        log.info("Registered tool: {} - {}", tool.getName(), tool.getDescription());
    }

    /**
     * 批量注册工具
     */
    public void registerTools(List<AiTool> tools) {
        tools.forEach(this::registerTool);
        log.info("Registered {} tools", tools.size());
    }

    /**
     * 获取所有可用工具
     */
    public List<ToolDefinition> getAvailableTools() {
        return registry.getAll();
    }

    /**
     * 获取工具定义
     */
    public ToolDefinition getToolDefinition(String toolName) {
        return registry.get(toolName);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        return registry.contains(toolName);
    }

    private Map<String, Object> createErrorResponse(String toolName, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("toolName", toolName);
        response.put("error", error);
        return response;
    }
}
