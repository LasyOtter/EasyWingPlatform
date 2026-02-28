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

import com.easywing.platform.cache.properties.CacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存服务
 * <p>
 * 提供本地缓存和Redis缓存的统一操作接口
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MultiLevelCacheService {

    private final CacheManager localCacheManager;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;

    /**
     * 从缓存获取数据
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @param type      返回类型
     * @param <T>       泛型类型
     * @return 缓存数据，不存在返回null
     */
    public <T> T get(String cacheName, String key, Class<T> type) {
        String fullKey = buildFullKey(cacheName, key);
        
        // 1. 先查本地缓存
        Cache localCache = localCacheManager.getCache(cacheName);
        if (localCache != null) {
            Cache.ValueWrapper wrapper = localCache.get(key);
            if (wrapper != null) {
                log.debug("Local cache hit: {}", fullKey);
                @SuppressWarnings("unchecked")
                T value = (T) wrapper.get();
                return value;
            }
        }
        
        // 2. 再查Redis
        String redisValue = redisTemplate.opsForValue().get(fullKey);
        if (redisValue != null) {
            log.debug("Redis cache hit: {}", fullKey);
            try {
                T value = objectMapper.readValue(redisValue, type);
                // 回填本地缓存
                if (localCache != null && value != null) {
                    localCache.put(key, value);
                }
                return value;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cache value: {}", fullKey, e);
            }
        }
        
        log.debug("Cache miss: {}", fullKey);
        return null;
    }

    /**
     * 将数据存入缓存
     *
     * @param cacheName   缓存名称
     * @param key         缓存key
     * @param value       缓存值
     * @param localExpire 本地缓存过期时间（秒）
     * @param redisExpire Redis缓存过期时间（秒）
     */
    public void put(String cacheName, String key, Object value, int localExpire, int redisExpire) {
        if (value == null && !properties.isCacheNullValues()) {
            return;
        }
        
        String fullKey = buildFullKey(cacheName, key);
        
        // 写入本地缓存
        Cache localCache = localCacheManager.getCache(cacheName);
        if (localCache != null) {
            localCache.put(key, value);
            log.debug("Local cache put: {}", fullKey);
        }
        
        // 写入Redis
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(fullKey, jsonValue, redisExpire, TimeUnit.SECONDS);
            log.debug("Redis cache put: {}, ttl: {}s", fullKey, redisExpire);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache value: {}", fullKey, e);
        }
    }

    /**
     * 清除缓存
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @param broadcast 是否广播通知其他节点
     */
    public void evict(String cacheName, String key, boolean broadcast) {
        String fullKey = buildFullKey(cacheName, key);
        
        // 清除本地缓存
        Cache localCache = localCacheManager.getCache(cacheName);
        if (localCache != null) {
            localCache.evict(key);
            log.debug("Local cache evict: {}", fullKey);
        }
        
        // 清除Redis
        redisTemplate.delete(fullKey);
        log.debug("Redis cache evict: {}", fullKey);
        
        // 广播通知其他节点
        if (broadcast) {
            broadcastEvict(cacheName, key);
        }
    }

    /**
     * 清除缓存（带广播）
     */
    public void evict(String cacheName, String key) {
        evict(cacheName, key, true);
    }

    /**
     * 清除整个缓存区域
     *
     * @param cacheName 缓存名称
     * @param broadcast 是否广播通知其他节点
     */
    public void clear(String cacheName, boolean broadcast) {
        // 清除本地缓存
        Cache localCache = localCacheManager.getCache(cacheName);
        if (localCache != null) {
            localCache.clear();
            log.debug("Local cache clear: {}", cacheName);
        }
        
        // 清除Redis（使用模式匹配删除）
        String pattern = properties.getKeyPrefix() + cacheName + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Redis cache clear: {}, deleted {} keys", cacheName, keys.size());
        }
        
        // 广播通知其他节点
        if (broadcast) {
            broadcastEvict(cacheName, null);
        }
    }

    /**
     * 清除整个缓存区域（带广播）
     */
    public void clear(String cacheName) {
        clear(cacheName, true);
    }

    /**
     * 广播缓存清理消息
     */
    private void broadcastEvict(String cacheName, String cacheKey) {
        try {
            String message = cacheKey != null ? cacheName + ":" + cacheKey : cacheName + ":*";
            RTopic topic = redissonClient.getTopic(properties.getRedis().getCacheTopic());
            topic.publish(message);
            log.debug("Broadcast cache evict: {}", message);
        } catch (Exception e) {
            log.error("Failed to broadcast cache evict message", e);
        }
    }

    /**
     * 构建完整的缓存key
     */
    private String buildFullKey(String cacheName, String key) {
        return properties.getKeyPrefix() + cacheName + ":" + key;
    }
}
