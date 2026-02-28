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
package com.easywing.platform.system.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.system.config.PageProperties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * PageHelper 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PageHelperTest {

    @Mock
    private PageProperties pageProperties;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PageHelper pageHelper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("规范化分页参数-默认参数")
    void normalizePage_DefaultParams() {
        // Given
        when(pageProperties.getMaxPage()).thenReturn(1000);
        when(pageProperties.getDefaultSize()).thenReturn(10);
        when(pageProperties.getMaxSize()).thenReturn(100);

        // When
        Page<String> result = pageHelper.normalizePage(1, 10);

        // Then
        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("规范化分页参数-页码小于1")
    void normalizePage_PageLessThanOne() {
        // Given
        when(pageProperties.getMaxPage()).thenReturn(1000);
        when(pageProperties.getDefaultSize()).thenReturn(10);
        when(pageProperties.getMaxSize()).thenReturn(100);

        // When
        Page<String> result = pageHelper.normalizePage(0, 10);

        // Then
        assertThat(result.getCurrent()).isEqualTo(1);
    }

    @Test
    @DisplayName("规范化分页参数-页码超过最大值")
    void normalizePage_PageExceedMax() {
        // Given
        when(pageProperties.getMaxPage()).thenReturn(1000);

        // When & Then
        assertThatThrownBy(() -> pageHelper.normalizePage(1001, 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("页码不能超过");
    }

    @Test
    @DisplayName("规范化分页参数-每页大小小于1")
    void normalizePage_SizeLessThanOne() {
        // Given
        when(pageProperties.getMaxPage()).thenReturn(1000);
        when(pageProperties.getDefaultSize()).thenReturn(10);
        when(pageProperties.getMaxSize()).thenReturn(100);

        // When
        Page<String> result = pageHelper.normalizePage(1, 0);

        // Then
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("规范化分页参数-每页大小超过最大值")
    void normalizePage_SizeExceedMax() {
        // Given
        when(pageProperties.getMaxPage()).thenReturn(1000);
        when(pageProperties.getDefaultSize()).thenReturn(10);
        when(pageProperties.getMaxSize()).thenReturn(100);

        // When
        Page<String> result = pageHelper.normalizePage(1, 200);

        // Then
        assertThat(result.getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("深度分页检查-正常页码")
    void checkDeepPage_NormalPage() {
        // Given
        when(pageProperties.getDeepPageThreshold()).thenReturn(100);

        // When & Then - should not throw exception
        pageHelper.checkDeepPage(50, 5000);
    }

    @Test
    @DisplayName("深度分页检查-深度分页")
    void checkDeepPage_DeepPage() {
        // Given
        when(pageProperties.getDeepPageThreshold()).thenReturn(100);

        // When & Then - should log warning but not throw exception
        pageHelper.checkDeepPage(150, 15000);
    }

    @Test
    @DisplayName("获取count缓存-成功")
    void getCountCache_Success() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(true);
        when(valueOperations.get("page:count:test:key")).thenReturn("100");

        // When
        Long result = pageHelper.getCountCache("test:key");

        // Then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("获取count缓存-未启用")
    void getCountCache_Disabled() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(false);

        // When
        Long result = pageHelper.getCountCache("test:key");

        // Then
        assertThat(result).isNull();
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("获取count缓存-缓存不存在")
    void getCountCache_NotFound() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(true);
        when(valueOperations.get("page:count:test:key")).thenReturn(null);

        // When
        Long result = pageHelper.getCountCache("test:key");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取count缓存-异常处理")
    void getCountCache_Exception() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(true);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        Long result = pageHelper.getCountCache("test:key");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("设置count缓存-成功")
    void setCountCache_Success() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(true);
        when(pageProperties.getCountCacheSeconds()).thenReturn(60);

        // When
        pageHelper.setCountCache("test:key", 100L);

        // Then
        verify(valueOperations)
                .set(
                        eq("page:count:test:key"),
                        eq("100"),
                        eq(60L),
                        eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("设置count缓存-未启用")
    void setCountCache_Disabled() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(false);

        // When
        pageHelper.setCountCache("test:key", 100L);

        // Then
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("设置count缓存-异常处理")
    void setCountCache_Exception() {
        // Given
        when(pageProperties.isCountCacheEnabled()).thenReturn(true);
        when(pageProperties.getCountCacheSeconds()).thenReturn(60);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any());

        // When & Then - should not throw exception
        pageHelper.setCountCache("test:key", 100L);
    }
}
