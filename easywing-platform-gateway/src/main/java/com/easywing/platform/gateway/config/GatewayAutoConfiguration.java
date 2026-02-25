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
package com.easywing.platform.gateway.config;

import com.easywing.platform.gateway.filter.gray.GrayReleaseFilter;
import com.easywing.platform.gateway.filter.jwt.JwtValidationFilter;
import com.easywing.platform.gateway.filter.logging.LoggingFilter;
import com.easywing.platform.gateway.filter.ratelimit.RateLimitFilter;
import com.easywing.platform.gateway.filter.ratelimit.RedisRateLimiter;
import com.easywing.platform.gateway.properties.GatewayProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 网关自动配置类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "easywing.gateway.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtValidationFilter jwtValidationFilter(GatewayProperties properties) {
        return new JwtValidationFilter(properties);
    }

    @Bean
    @ConditionalOnBean({ReactiveStringRedisTemplate.class, RedisRateLimiter.class})
    @ConditionalOnProperty(prefix = "easywing.gateway.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitFilter rateLimitFilter(GatewayProperties properties,
                                           ReactiveStringRedisTemplate redisTemplate,
                                           RedisScript<Long> rateLimitScript) {
        return new RateLimitFilter(properties, redisTemplate, rateLimitScript);
    }

    @Bean
    public RedisScript<Long> rateLimitScript() {
        return new RedisRateLimiter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.gateway.gray", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GrayReleaseFilter grayReleaseFilter(GatewayProperties properties) {
        return new GrayReleaseFilter(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.gateway.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingFilter loggingFilter(GatewayProperties properties) {
        return new LoggingFilter(properties);
    }
}