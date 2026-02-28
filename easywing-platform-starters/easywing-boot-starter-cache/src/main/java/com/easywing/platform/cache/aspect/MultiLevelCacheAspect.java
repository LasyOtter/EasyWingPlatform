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
package com.easywing.platform.cache.aspect;

import com.easywing.platform.cache.annotation.CacheEvict;
import com.easywing.platform.cache.annotation.CachePut;
import com.easywing.platform.cache.annotation.MultiLevelCache;
import com.easywing.platform.cache.properties.CacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存切面
 * <p>
 * 处理 @MultiLevelCache, @CacheEvict, @CachePut 注解
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class MultiLevelCacheAspect {

    private final CacheManager localCacheManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;
    
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 处理 @MultiLevelCache 注解
     */
    @Around("@annotation(multiLevelCache)")
    public Object aroundCache(ProceedingJoinPoint point, MultiLevelCache multiLevelCache) throws Throwable {
        String cacheName = multiLevelCache.value();
        String cacheKey = generateKey(point, multiLevelCache.key());
        String fullKey = buildFullKey(cacheName, cacheKey);
        
        // 1. 先查本地缓存
        Cache localCache = localCacheManager.getCache(cacheName);
        Cache.ValueWrapper localValue = localCache != null ? localCache.get(cacheKey) : null;
        if (localValue != null) {
            log.debug("Local cache hit: {}", fullKey);
            return localValue.get();
        }
        
        // 2. 再查Redis
        String redisValue = redisTemplate.opsForValue().get(fullKey);
        if (redisValue != null) {
            log.debug("Redis cache hit: {}", fullKey);
            Object value = deserialize(redisValue);
            // 回填本地缓存
            if (localCache != null && value != null) {
                localCache.put(cacheKey, value);
            }
            return value;
        }
        
        log.debug("Cache miss: {}", fullKey);
        
        // 3. 执行实际查询
        Object result = point.proceed();
        
        // 4. 写入缓存
        if (result != null || multiLevelCache.cacheNull()) {
            int localExpire = multiLevelCache.localExpire();
            int redisExpire = multiLevelCache.redisExpire();
            
            // 写入本地缓存
            if (localCache != null) {
                localCache.put(cacheKey, result);
            }
            
            // 写入Redis
            try {
                String jsonValue = serialize(result);
                redisTemplate.opsForValue().set(fullKey, jsonValue, redisExpire, TimeUnit.SECONDS);
                log.debug("Cache put: {}, localExpire: {}s, redisExpire: {}s", fullKey, localExpire, redisExpire);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize cache value: {}", fullKey, e);
            }
        }
        
        return result;
    }

    /**
     * 处理 @CacheEvict 注解
     */
    @Around("@annotation(cacheEvict)")
    public Object aroundEvict(ProceedingJoinPoint point, CacheEvict cacheEvict) throws Throwable {
        String cacheName = cacheEvict.value();
        
        // 如果需要在方法执行前清理缓存
        if (cacheEvict.beforeInvocation()) {
            performEvict(cacheName, cacheEvict.key(), cacheEvict.allEntries(), point);
        }
        
        try {
            Object result = point.proceed();
            
            // 默认在方法执行成功后清理缓存
            if (!cacheEvict.beforeInvocation()) {
                performEvict(cacheName, cacheEvict.key(), cacheEvict.allEntries(), point);
            }
            
            return result;
        } catch (Exception e) {
            // 如果方法执行失败且不是beforeInvocation，则不清理缓存
            if (!cacheEvict.beforeInvocation()) {
                log.warn("Method execution failed, cache not evicted: {}", cacheName);
            }
            throw e;
        }
    }

    /**
     * 处理 @CachePut 注解
     */
    @Around("@annotation(cachePut)")
    public Object aroundPut(ProceedingJoinPoint point, CachePut cachePut) throws Throwable {
        // CachePut总是执行方法
        Object result = point.proceed();
        
        // 检查条件
        if (shouldPut(cachePut, result)) {
            String cacheName = cachePut.value();
            String cacheKey = generateKey(point, cachePut.key());
            String fullKey = buildFullKey(cacheName, cacheKey);
            
            // 检查unless条件
            if (!shouldSkip(cachePut.unless(), result, point)) {
                // 更新本地缓存
                Cache localCache = localCacheManager.getCache(cacheName);
                if (localCache != null) {
                    localCache.put(cacheKey, result);
                }
                
                // 更新Redis
                try {
                    String jsonValue = serialize(result);
                    redisTemplate.opsForValue().set(fullKey, jsonValue, cachePut.redisExpire(), TimeUnit.SECONDS);
                    log.debug("Cache put (update): {}", fullKey);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize cache value: {}", fullKey, e);
                }
            }
        }
        
        return result;
    }

    /**
     * 执行缓存清理
     */
    private void performEvict(String cacheName, String keyExpression, boolean allEntries, ProceedingJoinPoint point) {
        if (allEntries) {
            // 清理整个缓存区域
            Cache localCache = localCacheManager.getCache(cacheName);
            if (localCache != null) {
                localCache.clear();
                log.debug("Local cache clear: {}", cacheName);
            }
            
            // 清理Redis
            String pattern = properties.getKeyPrefix() + cacheName + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Redis cache clear: {}, deleted {} keys", cacheName, keys.size());
            }
        } else {
            // 清理指定key
            String cacheKey = generateKey(point, keyExpression);
            String fullKey = buildFullKey(cacheName, cacheKey);
            
            // 清理本地缓存
            Cache localCache = localCacheManager.getCache(cacheName);
            if (localCache != null) {
                localCache.evict(cacheKey);
                log.debug("Local cache evict: {}", fullKey);
            }
            
            // 清理Redis
            redisTemplate.delete(fullKey);
            log.debug("Redis cache evict: {}", fullKey);
        }
    }

    /**
     * 生成缓存key
     */
    private String generateKey(ProceedingJoinPoint point, String keyExpression) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            // 默认使用所有参数的hashCode
            Object[] args = point.getArgs();
            return String.valueOf(Arrays.hashCode(args));
        }
        
        // 解析SpEL表达式
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object[] args = point.getArgs();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        
        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
            }
        }
        
        Expression expression = parser.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        return Objects.toString(value, "");
    }

    /**
     * 构建完整的缓存key
     */
    private String buildFullKey(String cacheName, String key) {
        return properties.getKeyPrefix() + cacheName + ":" + key;
    }

    /**
     * 序列化对象
     */
    private String serialize(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 反序列化对象
     */
    private Object deserialize(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cache value", e);
            return null;
        }
    }

    /**
     * 检查是否应该更新缓存
     */
    private boolean shouldPut(CachePut cachePut, Object result) {
        if (result == null && !cachePut.cacheNull()) {
            return false;
        }
        
        if (!cachePut.condition().isEmpty()) {
            // 解析条件表达式
            // 简化处理，实际应该解析SpEL
            return true;
        }
        
        return true;
    }

    /**
     * 检查是否应该跳过缓存
     */
    private boolean shouldSkip(String unlessExpression, Object result, ProceedingJoinPoint point) {
        if (unlessExpression == null || unlessExpression.isEmpty()) {
            return false;
        }
        
        // 解析SpEL表达式
        MethodSignature signature = (MethodSignature) point.getSignature();
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", result);
        
        try {
            Expression expression = parser.parseExpression(unlessExpression);
            Boolean value = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            log.warn("Failed to evaluate unless expression: {}", unlessExpression, e);
            return false;
        }
    }
}
