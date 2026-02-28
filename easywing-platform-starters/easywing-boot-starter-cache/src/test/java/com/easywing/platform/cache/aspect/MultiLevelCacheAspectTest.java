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

import com.easywing.platform.cache.annotation.MultiLevelCache;
import com.easywing.platform.cache.properties.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 多级缓存切面测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MultiLevelCacheAspectTest {

    @Mock
    private CacheManager localCacheManager;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CacheProperties properties;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Cache localCache;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MultiLevelCacheAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new MultiLevelCacheAspect(localCacheManager, redisTemplate, objectMapper, properties);
        when(properties.getKeyPrefix()).thenReturn("easywing:");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("测试本地缓存命中")
    void testLocalCacheHit() throws Throwable {
        // 准备测试数据
        MultiLevelCache annotation = createAnnotation("user", "#userId", 60, 300, false);
        Object cachedValue = new Object();
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        
        when(localCacheManager.getCache("user")).thenReturn(localCache);
        when(localCache.get("123")).thenReturn(wrapper);
        when(wrapper.get()).thenReturn(cachedValue);

        // 执行测试
        Object result = aspect.aroundCache(joinPoint, annotation);

        // 验证
        assertEquals(cachedValue, result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("测试Redis缓存命中并回填本地缓存")
    void testRedisCacheHit() throws Throwable {
        // 准备测试数据
        MultiLevelCache annotation = createAnnotation("user", "#userId", 60, 300, false);
        String jsonValue = "{\"id\":123,\"name\":\"test\"}";
        Object deserializedValue = new Object();
        
        when(localCacheManager.getCache("user")).thenReturn(localCache);
        when(localCache.get("123")).thenReturn(null);
        when(valueOperations.get("easywing:user:123")).thenReturn(jsonValue);
        when(objectMapper.readValue(jsonValue, Object.class)).thenReturn(deserializedValue);

        // 执行测试
        Object result = aspect.aroundCache(joinPoint, annotation);

        // 验证
        assertEquals(deserializedValue, result);
        verify(localCache).put("123", deserializedValue);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("测试缓存未命中并执行方法")
    void testCacheMiss() throws Throwable {
        // 准备测试数据
        MultiLevelCache annotation = createAnnotation("user", "#userId", 60, 300, false);
        Object methodResult = new Object();
        
        when(localCacheManager.getCache("user")).thenReturn(localCache);
        when(localCache.get("123")).thenReturn(null);
        when(valueOperations.get("easywing:user:123")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn(methodResult);
        when(objectMapper.writeValueAsString(methodResult)).thenReturn("{\"id\":123}");

        // 执行测试
        Object result = aspect.aroundCache(joinPoint, annotation);

        // 验证
        assertEquals(methodResult, result);
        verify(joinPoint).proceed();
        verify(localCache).put("123", methodResult);
        verify(valueOperations).set(eq("easywing:user:123"), anyString(), eq(300L), any());
    }

    @Test
    @DisplayName("测试不缓存null值")
    void testDoNotCacheNull() throws Throwable {
        // 准备测试数据
        MultiLevelCache annotation = createAnnotation("user", "#userId", 60, 300, false);
        
        when(localCacheManager.getCache("user")).thenReturn(localCache);
        when(localCache.get("123")).thenReturn(null);
        when(valueOperations.get("easywing:user:123")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn(null);

        // 执行测试
        Object result = aspect.aroundCache(joinPoint, annotation);

        // 验证
        assertNull(result);
        verify(localCache, never()).put(any(), any());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("测试缓存null值")
    void testCacheNull() throws Throwable {
        // 准备测试数据
        MultiLevelCache annotation = createAnnotation("user", "#userId", 60, 300, true);
        
        when(localCacheManager.getCache("user")).thenReturn(localCache);
        when(localCache.get("123")).thenReturn(null);
        when(valueOperations.get("easywing:user:123")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn(null);
        when(objectMapper.writeValueAsString(null)).thenReturn("null");

        // 执行测试
        Object result = aspect.aroundCache(joinPoint, annotation);

        // 验证
        assertNull(result);
        verify(localCache).put("123", null);
        verify(valueOperations).set(eq("easywing:user:123"), anyString(), eq(300L), any());
    }

    private MultiLevelCache createAnnotation(String value, String key, int localExpire, int redisExpire, boolean cacheNull) {
        return new MultiLevelCache() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public String key() {
                return key;
            }

            @Override
            public int localExpire() {
                return localExpire;
            }

            @Override
            public int redisExpire() {
                return redisExpire;
            }

            @Override
            public boolean cacheNull() {
                return cacheNull;
            }

            @Override
            public KeyGenerator keyGenerator() {
                return KeyGenerator.DEFAULT;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return MultiLevelCache.class;
            }
        };
    }
}
