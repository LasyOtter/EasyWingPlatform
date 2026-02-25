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
package com.easywing.platform.gateway.filter.ratelimit;

import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 限流过滤器测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private RedisRateLimiter rateLimitScript;

    private RateLimitFilter filter;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        RateLimitProperties rateLimitProperties = properties.getRateLimit();
        rateLimitProperties.setEnabled(true);
        rateLimitProperties.setDefaultRate(100);
        rateLimitProperties.setDefaultCapacity(200);
        rateLimitProperties.setLocalCacheSize(1000);
        
        filter = new RateLimitFilter(properties, redisTemplate, rateLimitScript);
    }

    @Test
    @DisplayName("Rate limit disabled - should pass through")
    void testRateLimitDisabled() {
        properties.getRateLimit().setEnabled(false);
        RateLimitFilter disabledFilter = new RateLimitFilter(properties, redisTemplate, rateLimitScript);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(disabledFilter.filter(exchange, chain))
                .verifyComplete();
        
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Local token bucket - should allow requests within capacity")
    void testLocalTokenBucketAllowsRequests() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange newExchange = MockServerWebExchange.from(request);
            StepVerifier.create(filter.filter(newExchange, chain))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("Rate limit exceeded - should return 429")
    void testRateLimitExceeded() {
        properties.getRateLimit().setDefaultRate(1);
        properties.getRateLimit().setDefaultCapacity(1);
        RateLimitFilter strictFilter = new RateLimitFilter(properties, redisTemplate, rateLimitScript);
        
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(Flux.just(-1L));
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        StepVerifier.create(strictFilter.filter(exchange, chain))
                .verifyComplete();
        
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Redis fallback - should use local bucket on Redis failure")
    void testRedisFallback() {
        properties.getRateLimit().setEnableFallback(true);
        properties.getRateLimit().setFallbackRate(50);
        
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        verify(chain).filter(any());
    }
}
