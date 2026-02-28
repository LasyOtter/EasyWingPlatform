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

import com.easywing.platform.cache.properties.CacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;

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
@AutoConfiguration
@ConditionalOnClass({Caffeine.class, RedissonClient.class})
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching
public class CacheAutoConfiguration {

    /**
     * Caffeine 缓存管理器
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "caffeine", matchIfMissing = false)
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager caffeineCacheManager(CacheProperties properties) {
        log.info("EasyWing Caffeine Cache Manager initialized");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(properties.getCaffeine().getInitialCapacity())
                .maximumSize(properties.getCaffeine().getMaximumSize())
                .expireAfterWrite(properties.getCaffeine().getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS));
        return cacheManager;
    }

    /**
     * Caffeine 对象配置
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.cache", name = "type", havingValue = "multi", matchIfMissing = true)
    public Caffeine<Object, Object> caffeineConfig(CacheProperties properties) {
        log.info("EasyWing Multi-level Cache initialized");
        return Caffeine.newBuilder()
                .initialCapacity(properties.getCaffeine().getInitialCapacity())
                .maximumSize(properties.getCaffeine().getMaximumSize())
                .expireAfterWrite(properties.getCaffeine().getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS);
    }

    public CacheAutoConfiguration() {
        log.info("EasyWing Cache AutoConfiguration initialized");
    }
}
