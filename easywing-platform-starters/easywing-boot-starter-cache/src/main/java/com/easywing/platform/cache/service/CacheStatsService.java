/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * 缓存统计服务
 * <p>
 * 提供缓存命中率、请求次数等统计信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CacheStatsService {

    private final CaffeineCacheManager localCacheManager;

    /**
     * 获取所有缓存的统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, CacheStatistics> getAllCacheStats() {
        Map<String, CacheStatistics> result = new HashMap<>();
        
        for (String cacheName : localCacheManager.getCacheNames()) {
            CacheStatistics stats = getCacheStats(cacheName);
            if (stats != null) {
                result.put(cacheName, stats);
            }
        }
        
        return result;
    }

    /**
     * 获取指定缓存的统计信息
     *
     * @param cacheName 缓存名称
     * @return 统计信息
     */
    public CacheStatistics getCacheStats(String cacheName) {
        org.springframework.cache.Cache springCache = localCacheManager.getCache(cacheName);
        if (springCache == null) {
            return null;
        }
        
        Object nativeCache = springCache.getNativeCache();
        if (nativeCache instanceof Cache<?, ?> cache) {
            CacheStats stats = cache.stats();
            return new CacheStatistics(
                    cacheName,
                    stats.hitCount(),
                    stats.missCount(),
                    stats.loadSuccessCount(),
                    stats.loadFailureCount(),
                    stats.totalLoadTime(),
                    stats.evictionCount(),
                    cache.estimatedSize()
            );
        }
        
        return null;
    }

    /**
     * 打印缓存统计信息
     */
    public void logCacheStats() {
        Map<String, CacheStatistics> stats = getAllCacheStats();
        stats.forEach((name, stat) -> {
            log.info("Cache [{}] - Hit Rate: {:.2f}%, Hits: {}, Misses: {}, Size: {}",
                    name, stat.hitRate() * 100, stat.hitCount(), stat.missCount(), stat.size());
        });
    }

    /**
     * 重置缓存统计信息
     */
    public void resetStats() {
        for (String cacheName : localCacheManager.getCacheNames()) {
            org.springframework.cache.Cache springCache = localCacheManager.getCache(cacheName);
            if (springCache != null) {
                Object nativeCache = springCache.getNativeCache();
                if (nativeCache instanceof Cache<?, ?> cache) {
                    // Caffeine不支持直接重置统计，需要重建缓存
                    log.info("Stats reset requested for cache: {}", cacheName);
                }
            }
        }
    }

    /**
     * 获取本地缓存的所有key
     *
     * @param cacheName 缓存名称
     * @return key集合
     */
    public Map<Object, Object> getCacheEntries(String cacheName) {
        Map<Object, Object> entries = new HashMap<>();
        
        org.springframework.cache.Cache springCache = localCacheManager.getCache(cacheName);
        if (springCache != null) {
            Object nativeCache = springCache.getNativeCache();
            if (nativeCache instanceof Cache<?, ?> cache) {
                ConcurrentMap<?, ?> map = cache.asMap();
                map.forEach((k, v) -> entries.put(k, v));
            }
        }
        
        return entries;
    }

    /**
     * 缓存统计信息
     */
    public record CacheStatistics(
            String cacheName,
            long hitCount,
            long missCount,
            long loadSuccessCount,
            long loadFailureCount,
            long totalLoadTime,
            long evictionCount,
            long size
    ) {
        /**
         * 计算命中率
         */
        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }

        /**
         * 计算平均加载时间（纳秒）
         */
        public double averageLoadTime() {
            long totalLoads = loadSuccessCount + loadFailureCount;
            return totalLoads == 0 ? 0.0 : (double) totalLoadTime / totalLoads;
        }

        /**
         * 计算请求总数
         */
        public long requestCount() {
            return hitCount + missCount;
        }
    }
}
