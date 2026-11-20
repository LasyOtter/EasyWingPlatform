package com.easywing.platform.ai.tool;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 工具执行缓存 - 缓存工具执行结果，避免重复调用
 */
@Slf4j
@Component
public class ToolExecutionCache {

    private final Cache<ToolCacheKey, Map<String, Object>> cache;
    private final ToolExecutor toolExecutor;

    public ToolExecutionCache(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();

        log.info("Tool execution cache initialized with max size: 1000, TTL: 10 minutes");
    }

    /**
     * 带缓存的工具执行
     */
    public Mono<Map<String, Object>> executeCached(String toolName, Map<String, Object> arguments) {
        ToolCacheKey key = new ToolCacheKey(toolName, arguments);

        // 尝试从缓存获取
        Map<String, Object> cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Cache hit for tool: {} with arguments: {}", toolName, arguments);
            // 返回副本：缓存对象被后续修改会污染后续命中（历史上此处直接改缓存对象）
            Map<String, Object> copy = new java.util.HashMap<>(cached);
            copy.put("fromCache", true);
            return Mono.just(copy);
        }

        // 缓存未命中，执行工具并缓存结果
        log.debug("Cache miss for tool: {}, executing...", toolName);
        return toolExecutor.executeTool(toolName, arguments)
                .doOnSuccess(result -> {
                    if (Boolean.TRUE.equals(result.get("success")) && isCacheable(toolName)) {
                        cache.put(key, result);
                        log.debug("Cached result for tool: {}", toolName);
                    }
                });
    }

    /**
     * 不可缓存的工具：结果随时间或外部状态变化。
     * <p>
     * {@code current_time} 每次调用结果不同；{@code http_request} 返回目标站点的实时数据，
     * 缓存会让调用方拿到过期响应。其余工具按幂等性判断，默认参与缓存。
     */
    private static final Set<String> NON_CACHEABLE_TOOLS = Set.of("current_time", "http_request");

    private boolean isCacheable(String toolName) {
        return toolName != null && !NON_CACHEABLE_TOOLS.contains(toolName);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.invalidateAll();
        log.info("Tool execution cache cleared");
    }

    /**
     * 清空特定工具的缓存
     */
    public void clearCache(String toolName) {
        cache.asMap().keySet().stream()
                .filter(key -> key.getToolName().equals(toolName))
                .forEach(cache::invalidate);
        log.info("Cleared cache for tool: {}", toolName);
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", stats.hitRate(),
                "evictionCount", stats.evictionCount(),
                "size", cache.estimatedSize()
        );
    }
}
