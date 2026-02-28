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
package com.easywing.platform.web.idempotent;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 幂等性切面单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private IdempotentAspect idempotentAspect;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotentAspect = new IdempotentAspect(redisTemplate);
    }

    @Test
    @DisplayName("首次请求应该成功执行")
    void testFirstRequestSuccess() throws Throwable {
        // Arrange
        Idempotent idempotent = createIdempotentAnnotation("#username", 30, "请勿重复提交");
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"username"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"testuser"});
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn(1L);

        // Act
        Object result = idempotentAspect.around(joinPoint, idempotent);

        // Assert
        assertEquals(1L, result);
        verify(joinPoint).proceed();
        verify(redisTemplate).expire(anyString(), eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("重复请求应该抛出异常")
    void testDuplicateRequestThrowsException() throws Throwable {
        // Arrange
        Idempotent idempotent = createIdempotentAnnotation("#username", 30, "该用户正在创建中");
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"username"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"testuser"});
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn("existing-lock");

        // Act & Assert
        BizException exception = assertThrows(BizException.class, () -> {
            idempotentAspect.around(joinPoint, idempotent);
        });

        assertEquals(ErrorCode.REQUEST_DUPLICATE, exception.getErrorCode());
        assertEquals("该用户正在创建中", exception.getDetail());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("业务执行失败应该释放锁")
    void testBusinessFailureReleasesLock() throws Throwable {
        // Arrange
        Idempotent idempotent = createIdempotentAnnotation("#username", 30, "请勿重复提交");
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"username"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"testuser"});
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            idempotentAspect.around(joinPoint, idempotent);
        });

        // 验证释放锁被调用
        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("使用空SPEL表达式应该生成默认key")
    void testEmptySpelGeneratesDefaultKey() throws Throwable {
        // Arrange
        Idempotent idempotent = createIdempotentAnnotation("", 60, "请勿重复提交");
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        Object result = idempotentAspect.around(joinPoint, idempotent);

        // Assert
        assertEquals("success", result);
        verify(valueOperations).setIfAbsent(contains("idempotent:"), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("过期时间小于5秒时不调整保留时间")
    void testShortExpireNoAdjustment() throws Throwable {
        // Arrange
        Idempotent idempotent = createIdempotentAnnotation("#username", 3, "请勿重复提交");
        
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"username"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"testuser"});
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(3L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        Object result = idempotentAspect.around(joinPoint, idempotent);

        // Assert
        assertEquals("success", result);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    private Idempotent createIdempotentAnnotation(String key, int expire, String message) {
        return new Idempotent() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public int expire() {
                return expire;
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    private Method getTestMethod() throws NoSuchMethodException {
        return this.getClass().getDeclaredMethod("testMethod", String.class);
    }

    @SuppressWarnings("unused")
    public void testMethod(String username) {
        // 测试方法
    }
}
