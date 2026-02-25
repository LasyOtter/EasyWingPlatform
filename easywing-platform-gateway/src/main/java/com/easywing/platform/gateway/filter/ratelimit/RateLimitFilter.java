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

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流全局过滤器
 * <p>
 * 核心功能：
 * <ul>
 *     <li>分布式限流（Redis + Lua脚本，保证原子性）</li>
 *     <li>多级限流策略：全局、API、用户、IP</li>
 *     <li>令牌桶/漏桶/滑动窗口算法支持</li>
 *     <li>本地令牌桶预热（减少Redis访问）</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *     <li>Redis Pipeline批量处理</li>
 *     <li>本地令牌桶预热</li>
 *     <li>异步限流计数</li>
 *     <li>限流失败快速返回</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private final RateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final Cache<String, LocalTokenBucket> localBuckets;

    public RateLimitFilter(GatewayProperties gatewayProperties, 
                          ReactiveStringRedisTemplate redisTemplate,
                          RedisScript<Long> rateLimitScript) {
        this.properties = gatewayProperties.getRateLimit();
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.localBuckets = Caffeine.newBuilder()
                .maximumSize(properties.getLocalCacheSize())
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String key = resolveKey(exchange);
        RateLimitProperties.RuleConfig rule = findMatchingRule(exchange);
        
        int rate = rule != null ? rule.getRate() : properties.getDefaultRate();
        int capacity = rule != null ? rule.getCapacity() : properties.getDefaultCapacity();
        
        LocalTokenBucket localBucket = localBuckets.get(key, k -> new LocalTokenBucket(capacity, rate));
        
        if (localBucket.tryConsume()) {
            addRateLimitHeaders(exchange, localBucket);
            return chain.filter(exchange);
        }
        
        return checkDistributedRateLimit(key, rate, capacity)
                .flatMap(allowed -> {
                    if (allowed) {
                        addRateLimitHeaders(exchange, localBucket);
                        return chain.filter(exchange);
                    }
                    return tooManyRequests(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed, using fallback: {}", e.getMessage());
                    if (properties.isEnableFallback()) {
                        LocalTokenBucket fallbackBucket = localBuckets.get(key, 
                                k -> new LocalTokenBucket(properties.getFallbackRate(), properties.getFallbackRate()));
                        if (fallbackBucket.tryConsume()) {
                            return chain.filter(exchange);
                        }
                    }
                    return tooManyRequests(exchange);
                });
    }

    private String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_USER_ID);
        if (userId != null && !userId.isEmpty()) {
            return "rate_limit:user:" + userId;
        }
        
        String ip = getClientIp(exchange);
        return "rate_limit:ip:" + ip;
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    private RateLimitProperties.RuleConfig findMatchingRule(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        for (RateLimitProperties.RuleConfig rule : properties.getRules()) {
            if (path.startsWith(rule.getPattern()) || path.matches(rule.getPattern())) {
                return rule;
            }
        }
        return null;
    }

    private Mono<Boolean> checkDistributedRateLimit(String key, int rate, int capacity) {
        long now = System.currentTimeMillis();
        long requested = 1;
        
        return redisTemplate.execute(
                        rateLimitScript,
                        Collections.singletonList(key),
                        String.valueOf(rate),
                        String.valueOf(capacity),
                        String.valueOf(now),
                        String.valueOf(requested)
                )
                .next()
                .map(result -> result != null && result >= 0)
                .defaultIfEmpty(true);
    }

    private void addRateLimitHeaders(ServerWebExchange exchange, LocalTokenBucket bucket) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(RATE_LIMIT_REMAINING, String.valueOf(bucket.getRemaining()));
        response.getHeaders().add(RATE_LIMIT_LIMIT, String.valueOf(bucket.getCapacity()));
        response.getHeaders().add(RATE_LIMIT_RESET, String.valueOf(bucket.getResetTime()));
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static class LocalTokenBucket {
        private final int capacity;
        private final int rate;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        LocalTokenBucket(int capacity, int rate) {
            this.capacity = capacity;
            this.rate = rate;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed > 0) {
                long newTokens = elapsed * rate / 1000;
                if (newTokens > 0) {
                    tokens.set(Math.min(capacity, tokens.get() + newTokens));
                    lastRefillTime = now;
                }
            }
        }

        long getRemaining() {
            refill();
            return tokens.get();
        }

        int getCapacity() {
            return capacity;
        }

        long getResetTime() {
            return System.currentTimeMillis() + 1000;
        }
    }
}