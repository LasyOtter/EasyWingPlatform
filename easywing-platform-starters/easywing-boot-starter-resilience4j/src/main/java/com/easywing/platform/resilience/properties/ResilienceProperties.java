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
package com.easywing.platform.resilience.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resilience4j 熔断限流配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.resilience")
public class ResilienceProperties {

    /**
     * 是否启用熔断限流
     */
    private boolean enabled = true;

    /**
     * 熔断器配置
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * 限流器配置
     */
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 舱壁隔离配置
     */
    private BulkheadConfig bulkhead = new BulkheadConfig();

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedNumberOfCallsInHalfOpenState = 10;
        private int slidingWindowSize = 100;
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 10;
    }

    @Data
    public static class RateLimiterConfig {
        private boolean enabled = true;
        private Duration timeoutDuration = Duration.ofSeconds(5);
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private int limitForPeriod = 100;
    }

    @Data
    public static class RetryConfig {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);
        private double multiplier = 1.5;
    }

    @Data
    public static class BulkheadConfig {
        private boolean enabled = true;
        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ofSeconds(1);
    }
}
