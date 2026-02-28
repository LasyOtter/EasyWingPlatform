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
package com.easywing.platform.cache.protector;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存保护器
 * <p>
 * 提供缓存穿透、击穿、雪崩防护
 * <ul>
 *     <li>缓存穿透：使用布隆过滤器 + 空值缓存</li>
 *     <li>缓存击穿：使用互斥锁 + 热点数据永不过期</li>
 *     <li>缓存雪崩：使用随机过期时间 + 多级缓存</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class CacheProtector {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    
    private static final String NULL_CACHE_VALUE = "NULL";
    private static final String LOCK_PREFIX = "cache:lock:";
    private static final String BLOOM_FILTER_PREFIX = "cache:bloom:";

    public CacheProtector(RedissonClient redissonClient, StringRedisTemplate redisTemplate) {
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 使用布隆过滤器防止缓存穿透
     *
     * @param cacheName      缓存名称
     * @param key            缓存key
     * @param loader         数据加载器
     * @param redisExpire    Redis过期时间（秒）
     * @param nullExpire     空值缓存过期时间（秒）
     * @param <T>            返回类型
     * @return 数据
     */
    public <T> T getWithBloomFilter(String cacheName, String key, Supplier<T> loader,
                                    int redisExpire, int nullExpire) {
        String fullKey = "cache:" + cacheName + ":" + key;
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + cacheName);
        
        // 1. 布隆过滤器判断key是否存在
        if (bloomFilter.isExists() && !bloomFilter.contains(key)) {
            log.debug("Bloom filter rejected: {}", key);
            return null;
        }
        
        // 2. 查询Redis
        String value = redisTemplate.opsForValue().get(fullKey);
        if (value != null) {
            if (NULL_CACHE_VALUE.equals(value)) {
                log.debug("Null cache hit: {}", key);
                return null;
            }
            log.debug("Redis cache hit: {}", key);
            return deserialize(value);
        }
        
        // 3. 加载数据
        T result = loader.get();
        
        // 4. 缓存结果（包括空值）
        if (result != null) {
            redisTemplate.opsForValue().set(fullKey, serialize(result), redisExpire, TimeUnit.SECONDS);
            // 添加到布隆过滤器
            bloomFilter.add(key);
        } else {
            // 缓存空值防止穿透
            redisTemplate.opsForValue().set(fullKey, NULL_CACHE_VALUE, nullExpire, TimeUnit.SECONDS);
            log.debug("Cached null value: {}", key);
        }
        
        return result;
    }

    /**
     * 使用互斥锁防止缓存击穿
     *
     * @param cacheName   缓存名称
     * @param key         缓存key
     * @param loader      数据加载器
     * @param redisExpire Redis过期时间（秒）
     * @param waitTime    等待锁的时间（秒）
     * @param <T>         返回类型
     * @return 数据
     */
    public <T> T getWithLock(String cacheName, String key, Supplier<T> loader,
                              int redisExpire, int waitTime) {
        String fullKey = "cache:" + cacheName + ":" + key;
        String lockKey = LOCK_PREFIX + cacheName + ":" + key;
        
        // 1. 查询Redis
        String value = redisTemplate.opsForValue().get(fullKey);
        if (value != null) {
            if (NULL_CACHE_VALUE.equals(value)) {
                return null;
            }
            return deserialize(value);
        }
        
        // 2. 获取分布式锁
        try {
            var lock = redissonClient.getLock(lockKey);
            boolean locked = lock.tryLock(waitTime, TimeUnit.SECONDS);
            
            if (locked) {
                try {
                    // 双重检查
                    value = redisTemplate.opsForValue().get(fullKey);
                    if (value != null) {
                        return NULL_CACHE_VALUE.equals(value) ? null : deserialize(value);
                    }
                    
                    // 加载数据
                    T result = loader.get();
                    
                    // 缓存结果
                    String cacheValue = result != null ? serialize(result) : NULL_CACHE_VALUE;
                    redisTemplate.opsForValue().set(fullKey, cacheValue, redisExpire, TimeUnit.SECONDS);
                    
                    return result;
                } finally {
                    lock.unlock();
                }
            } else {
                log.warn("Failed to acquire lock for key: {}", key);
                // 获取锁失败，直接加载数据（降级处理）
                return loader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for key: {}", key, e);
            return loader.get();
        }
    }

    /**
     * 使用随机过期时间防止缓存雪崩
     *
     * @param baseExpire  基础过期时间（秒）
     * @param randomRange 随机范围（秒）
     * @return 随机过期时间
     */
    public int getRandomExpire(int baseExpire, int randomRange) {
        int random = (int) (Math.random() * randomRange);
        return baseExpire + random;
    }

    /**
     * 初始化布隆过滤器
     *
     * @param cacheName        缓存名称
     * @param expectedItems    预期元素数量
     * @param falseProbability 误判率
     */
    public void initBloomFilter(String cacheName, long expectedItems, double falseProbability) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + cacheName);
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(expectedItems, falseProbability);
            log.info("Bloom filter initialized: {} with {} expected items, {} false probability",
                    cacheName, expectedItems, falseProbability);
        }
    }

    /**
     * 添加元素到布隆过滤器
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @return 是否添加成功
     */
    public boolean addToBloomFilter(String cacheName, String key) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + cacheName);
        return bloomFilter.add(key);
    }

    /**
     * 判断布隆过滤器是否可能包含元素
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @return 是否可能包含
     */
    public boolean mightContain(String cacheName, String key) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + cacheName);
        return bloomFilter.contains(key);
    }

    private String serialize(Object value) {
        // 简化处理，实际应使用JSON序列化
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String value) {
        // 简化处理，实际应使用JSON反序列化
        return (T) value;
    }
}
