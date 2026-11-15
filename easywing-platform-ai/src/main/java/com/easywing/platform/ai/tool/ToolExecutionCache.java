package com.easywing.platform.ai.tool;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
            cached.put("fromCache", true);
            return Mono.just(cached);
        }

        // 缓存未命中，执行工具并缓存结果
        log.debug("Cache miss for tool: {}, executing...", toolName);
        return toolExecutor.executeTool(toolName, arguments).doOnSuccess(result -> {
            if (Boolean.TRUE.equals(result.get("success")) && isCacheable(toolName)) {
                cache.put(key, result);
                log.debug("Cached result for tool: {}", toolName);
            }
        });
    }

    /**
     * 判断工具结果是否可缓存
     */
    private boolean isCacheable(String toolName) {
        // 某些工具的结果不应该被缓存，例如：
        // - current_time (时间总是变化的)
        // - http_request (可能返回实时数据)
        return !toolName.equals("current_time");
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
                "size", cache.estimatedSize());
    }
}
