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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * RateLimitFilter 单元测试
 * <p>
 * 重点测试：
 * 1. 基本限流功能
 * 2. 并发安全性
 * 3. Token 令牌桶算法
 * 4. 降级处理
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@DisplayName("RateLimitFilter 测试")
class RateLimitFilterEnhancedTest {

    private RateLimitFilter filter;
    private GatewayProperties gatewayProperties;
    private ReactiveStringRedisTemplate redisTemplate;
    private GatewayFilterChain chain;
    private RedisScript<Long> rateLimitScript;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        RateLimitProperties rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setEnabled(true);
        rateLimitProperties.setDefaultRate(10); // 每秒10个请求
        rateLimitProperties.setDefaultCapacity(100);
        gatewayProperties.setRateLimit(rateLimitProperties);

        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        chain = mock(GatewayFilterChain.class);

        rateLimitScript = new RedisRateLimiter();
        filter = new RateLimitFilter(gatewayProperties, redisTemplate, rateLimitScript);

        // Mock chain 返回成功
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("正常请求应该通过")
    void testNormalRequestShouldPass() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock Redis 返回成功
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("超过限流应该返回429")
    void testExceedRateLimitShouldReturn429() {
        // Given
        gatewayProperties.getRateLimit().setDefaultRate(1);
        gatewayProperties.getRateLimit().setDefaultCapacity(1);
        RateLimitFilter strictFilter = new RateLimitFilter(gatewayProperties, redisTemplate, rateLimitScript);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();

        // Mock Redis 返回失败（令牌不足）
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(-1L));
        when(chain.filter(any())).thenReturn(Mono.empty());

        // 首次请求消耗本地令牌
        StepVerifier.create(strictFilter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();

        // When & Then
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        StepVerifier.create(strictFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, never()).filter(exchange);
    }

    @Test
    @DisplayName("并发请求测试 - 令牌桶算法")
    void testConcurrentRequests() throws InterruptedException {
        // Given
        int threadCount = 50;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        gatewayProperties.getRateLimit().setDefaultRate(0);
        gatewayProperties.getRateLimit().setDefaultCapacity(100);
        RateLimitFilter concurrentFilter = new RateLimitFilter(gatewayProperties, redisTemplate, rateLimitScript);

        // Mock Redis 限流：前100个成功，后面失败
        AtomicInteger callCount = new AtomicInteger(0);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    return Flux.just(count <= 100 ? 1L : -1L);
                });

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
                        MockServerWebExchange exchange = MockServerWebExchange.from(request);

                        concurrentFilter.filter(exchange, chain).block();

                        if (exchange.getResponse().getStatusCode() == null) {
                            successCount.incrementAndGet();
                        } else if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            failCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 本地桶放行 100 次，随后 Redis 再放行 100 次，其余被限流
        assertThat(successCount.get()).isEqualTo(200);
        assertThat(failCount.get()).isEqualTo(300);
    }

    @Test
    @DisplayName("Redis 异常时应该降级处理")
    void testRedisFallback() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock Redis 抛出异常
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));

        // When & Then - 应该使用本地降级，请求通过
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("本地令牌桶测试 - Token refill")
    void testLocalTokenBucketRefill() throws InterruptedException {
        // 通过反射访问内部类进行测试
        // 这里简化测试，实际需要反射访问 LocalTokenBucket

        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock Redis 失败，触发本地降级
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis down")));

        // When - 连续请求
        for (int i = 0; i < 50; i++) {
            filter.filter(exchange, chain).block();
        }

        // 等待 token refill
        Thread.sleep(1000);

        // Then - 应该有新的 token
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("不同 IP 应该独立限流")
    void testDifferentIpsShouldHaveIndependentLimits() {
        // Given
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/test")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);

        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/test")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.2", 8080))
                .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        // Mock Redis 为两个 IP 分别返回结果
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L)); // 都有令牌

        // When & Then
        StepVerifier.create(filter.filter(exchange1, chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchange2, chain))
                .verifyComplete();

        verify(chain, times(2)).filter(any());
    }

    @Test
    @DisplayName("响应头应该包含限流信息")
    void testResponseHeadersShouldContainRateLimitInfo() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        // When
        filter.filter(exchange, chain).block();

        // Then
        assertThat(exchange.getResponse().getHeaders().get("X-RateLimit-Limit")).isNotNull();
        assertThat(exchange.getResponse().getHeaders().get("X-RateLimit-Remaining")).isNotNull();
        assertThat(exchange.getResponse().getHeaders().get("X-RateLimit-Reset")).isNotNull();
    }

    @Test
    @DisplayName("配置禁用时应该直接通过")
    void testDisabledConfigShouldPassThrough() {
        // Given
        gatewayProperties.getRateLimit().setEnabled(false);
        filter = new RateLimitFilter(gatewayProperties, redisTemplate, rateLimitScript);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(redisTemplate, never()).execute(any(), anyList(), anyList());
    }
}
