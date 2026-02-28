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
package com.easywing.platform.cache.config;

import com.easywing.platform.cache.aspect.MultiLevelCacheAspect;
import com.easywing.platform.cache.listener.CacheEvictListener;
import com.easywing.platform.cache.properties.CacheProperties;
import com.easywing.platform.cache.protector.CacheProtector;
import com.easywing.platform.cache.service.CacheStatsService;
import com.easywing.platform.cache.service.MultiLevelCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

/**
 * 缓存自动配置
 * <p>
 * 支持三种缓存模式:
 * <ul>
 *     <li>caffeine - 纯本地缓存</li>
 *     <li>redis - 纯分布式缓存</li>
 *     <li>multi - 多级缓存(本地 + 分布式)</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({Caffeine.class, RedissonClient.class})
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching
public class CacheAutoConfiguration {

    /**
     * Caffeine 本地缓存管理器
     */
    @Bean("localCacheManager")
    @ConditionalOnMissingBean(name = "localCacheManager")
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public CacheManager localCacheManager(CacheProperties properties) {
        log.info("EasyWing Local Cache Manager (Caffeine) initialized");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(properties.getCaffeine().getInitialCapacity())
                .maximumSize(properties.getCaffeine().getMaximumSize())
                .expireAfterWrite(properties.getCaffeine().getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS);
        
        if (properties.getCaffeine().getExpireAfterAccess() != null) {
            caffeine.expireAfterAccess(properties.getCaffeine().getExpireAfterAccess().toMillis(), TimeUnit.MILLISECONDS);
        }
        
        if (properties.getCaffeine().isRecordStats() && properties.isStatsEnabled()) {
            caffeine.recordStats();
        }
        
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }

    /**
     * Caffeine 缓存管理器（纯本地缓存模式）
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "caffeine")
    public CacheManager caffeineCacheManager(CacheProperties properties) {
        log.info("EasyWing Caffeine Cache Manager (standalone) initialized");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(properties.getCaffeine().getInitialCapacity())
                .maximumSize(properties.getCaffeine().getMaximumSize())
                .expireAfterWrite(properties.getCaffeine().getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS)
                .recordStats(properties.isStatsEnabled()));
        return cacheManager;
    }

    /**
     * StringRedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * ObjectMapper for cache serialization
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * 多级缓存服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public MultiLevelCacheService multiLevelCacheService(
            CacheManager localCacheManager,
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            ObjectMapper cacheObjectMapper,
            CacheProperties properties) {
        log.info("EasyWing Multi-level Cache Service initialized");
        return new MultiLevelCacheService(localCacheManager, redisTemplate, redissonClient, cacheObjectMapper, properties);
    }

    /**
     * 缓存统计服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "stats-enabled", havingValue = "true", matchIfMissing = true)
    public CacheStatsService cacheStatsService(CacheManager localCacheManager) {
        log.info("EasyWing Cache Stats Service initialized");
        if (localCacheManager instanceof CaffeineCacheManager caffeineCacheManager) {
            return new CacheStatsService(caffeineCacheManager);
        }
        log.warn("LocalCacheManager is not CaffeineCacheManager, stats may not be available");
        return new CacheStatsService(new CaffeineCacheManager());
    }

    /**
     * 多级缓存切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public MultiLevelCacheAspect multiLevelCacheAspect(
            CacheManager localCacheManager,
            StringRedisTemplate redisTemplate,
            ObjectMapper cacheObjectMapper,
            CacheProperties properties) {
        log.info("EasyWing Multi-level Cache Aspect initialized");
        return new MultiLevelCacheAspect(localCacheManager, redisTemplate, cacheObjectMapper, properties);
    }

    /**
     * 缓存清理监听器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public CacheEvictListener cacheEvictListener(CacheManager localCacheManager) {
        log.info("EasyWing Cache Evict Listener initialized");
        return new CacheEvictListener(localCacheManager);
    }

    /**
     * Redis消息订阅配置
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public Object cacheTopicSubscription(
            RedissonClient redissonClient,
            CacheEvictListener cacheEvictListener,
            CacheProperties properties) {
        String topicName = properties.getRedis().getCacheTopic();
        RTopic topic = redissonClient.getTopic(topicName);
        topic.addListener(String.class, cacheEvictListener);
        log.info("Subscribed to cache topic: {}", topicName);
        return topic;
    }

    /**
     * 缓存保护器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public CacheProtector cacheProtector(
            RedissonClient redissonClient,
            StringRedisTemplate redisTemplate) {
        log.info("EasyWing Cache Protector initialized");
        return new CacheProtector(redissonClient, redisTemplate);
    }

    public CacheAutoConfiguration() {
        log.info("EasyWing Cache AutoConfiguration initialized");
    }
}
