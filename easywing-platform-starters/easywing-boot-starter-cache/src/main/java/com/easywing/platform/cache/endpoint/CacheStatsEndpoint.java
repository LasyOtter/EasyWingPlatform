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
package com.easywing.platform.cache.endpoint;

import com.easywing.platform.cache.service.CacheStatsService;
import com.easywing.platform.cache.service.CacheStatsService.CacheStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 缓存统计端点
 * <p>
 * 提供缓存命中率、请求次数等统计信息的HTTP接口
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/actuator/cache")
@RequiredArgsConstructor
@ConditionalOnBean(CacheStatsService.class)
public class CacheStatsEndpoint {

    private final CacheStatsService cacheStatsService;

    /**
     * 获取所有缓存的统计信息
     *
     * @return 统计信息Map
     */
    @GetMapping("/stats")
    public Map<String, CacheStatistics> getAllCacheStats() {
        return cacheStatsService.getAllCacheStats();
    }

    /**
     * 获取指定缓存的统计信息
     *
     * @param cacheName 缓存名称
     * @return 统计信息
     */
    @GetMapping("/stats/{cacheName}")
    public CacheStatistics getCacheStats(@PathVariable String cacheName) {
        return cacheStatsService.getCacheStats(cacheName);
    }

    /**
     * 获取缓存统计摘要
     *
     * @return 统计摘要
     */
    @GetMapping("/summary")
    public CacheSummary getCacheSummary() {
        Map<String, CacheStatistics> stats = cacheStatsService.getAllCacheStats();
        
        long totalHitCount = 0;
        long totalMissCount = 0;
        long totalSize = 0;
        long totalEvictions = 0;
        
        for (CacheStatistics stat : stats.values()) {
            totalHitCount += stat.hitCount();
            totalMissCount += stat.missCount();
            totalSize += stat.size();
            totalEvictions += stat.evictionCount();
        }
        
        double overallHitRate = (totalHitCount + totalMissCount) == 0 
                ? 0.0 
                : (double) totalHitCount / (totalHitCount + totalMissCount);
        
        return new CacheSummary(
                stats.size(),
                totalHitCount,
                totalMissCount,
                overallHitRate,
                totalSize,
                totalEvictions
        );
    }

    /**
     * 缓存统计摘要
     */
    public record CacheSummary(
            int cacheCount,
            long totalHitCount,
            long totalMissCount,
            double overallHitRate,
            long totalSize,
            long totalEvictions
    ) {
        /**
         * 获取总请求次数
         */
        public long totalRequestCount() {
            return totalHitCount + totalMissCount;
        }
    }
}
