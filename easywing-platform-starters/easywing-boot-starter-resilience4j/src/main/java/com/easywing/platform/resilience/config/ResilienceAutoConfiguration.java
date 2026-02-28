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
package com.easywing.platform.resilience.config;

import com.easywing.platform.resilience.properties.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Resilience4j 自动配置
 * <p>
 * 提供熔断、限流、重试、舱壁隔离等容错能力
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
@EnableConfigurationProperties(ResilienceProperties.class)
@ConditionalOnProperty(prefix = "easywing.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResilienceAutoConfiguration {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(properties.getCircuitBreaker().getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getCircuitBreaker().getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
                .failureRateThreshold(properties.getCircuitBreaker().getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getCircuitBreaker().getMinimumNumberOfCalls())
                .build();
        log.info("EasyWing Resilience4j CircuitBreaker Registry initialized");
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(ResilienceProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .timeoutDuration(properties.getRateLimiter().getTimeoutDuration())
                .limitRefreshPeriod(properties.getRateLimiter().getLimitRefreshPeriod())
                .limitForPeriod(properties.getRateLimiter().getLimitForPeriod())
                .build();
        log.info("EasyWing Resilience4j RateLimiter Registry initialized");
        return RateLimiterRegistry.of(config);
    }

    @Bean
    public RetryRegistry retryRegistry(ResilienceProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .waitDuration(properties.getRetry().getWaitDuration())
                .build();
        log.info("EasyWing Resilience4j Retry Registry initialized");
        return RetryRegistry.of(config);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry(ResilienceProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(properties.getBulkhead().getMaxWaitDuration())
                .build();
        log.info("EasyWing Resilience4j Bulkhead Registry initialized");
        return BulkheadRegistry.of(config);
    }

    public ResilienceAutoConfiguration() {
        log.info("EasyWing Resilience4j AutoConfiguration initialized");
    }
}
