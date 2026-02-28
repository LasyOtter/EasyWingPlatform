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
package com.easywing.platform.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.cache")
public class CacheProperties {

    /**
     * 是否启用缓存
     */
    private boolean enabled = true;

    /**
     * 缓存类型: redis, caffeine, multi
     */
    private String type = "multi";

    /**
     * 默认缓存过期时间
     */
    private Duration defaultTtl = Duration.ofMinutes(30);

    /**
     * 缓存键前缀
     */
    private String keyPrefix = "easywing:";

    /**
     * 是否缓存null值
     */
    private boolean cacheNullValues = false;

    /**
     * 是否启用统计信息
     */
    private boolean statsEnabled = true;

    /**
     * Caffeine 本地缓存配置
     */
    private CaffeineConfig caffeine = new CaffeineConfig();

    /**
     * Redis 分布式缓存配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 自定义缓存配置（按缓存名称）
     */
    private Map<String, CacheTtlConfig> caches = new HashMap<>();

    @Data
    public static class CaffeineConfig {
        /**
         * 初始容量
         */
        private int initialCapacity = 100;

        /**
         * 最大容量
         */
        private int maximumSize = 10000;

        /**
         * 写入后过期时间
         */
        private Duration expireAfterWrite = Duration.ofMinutes(1);

        /**
         * 访问后过期时间
         */
        private Duration expireAfterAccess;

        /**
         * 是否记录统计信息
         */
        private boolean recordStats = true;
    }

    @Data
    public static class RedisConfig {
        /**
         * 默认过期时间
         */
        private Duration defaultTtl = Duration.ofMinutes(10);

        /**
         * 批量操作大小
         */
        private int batchSize = 100;

        /**
         * 是否使用键前缀
         */
        private boolean useKeyPrefix = true;

        /**
         * 缓存主题名称（用于集群间缓存同步）
         */
        private String cacheTopic = "cache:evict";
    }

    /**
     * 单个缓存的TTL配置
     */
    @Data
    public static class CacheTtlConfig {
        /**
         * 本地缓存过期时间（秒）
         */
        private int localExpire = 60;

        /**
         * Redis缓存过期时间（秒）
         */
        private int redisExpire = 300;

        /**
         * 是否缓存null值
         */
        private boolean cacheNull = false;
    }

    /**
     * 获取指定缓存名称的配置
     */
    public CacheTtlConfig getCacheConfig(String cacheName) {
        return caches.getOrDefault(cacheName, new CacheTtlConfig());
    }
}
