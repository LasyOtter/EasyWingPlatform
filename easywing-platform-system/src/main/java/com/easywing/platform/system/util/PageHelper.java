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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.config.PageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分页工具类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageHelper {

    private final PageProperties pageProperties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 规范化分页参数
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 规范化后的Page对象
     */
    public <T> Page<T> normalizePage(long current, long size) {
        // 页码不能小于1
        if (current < 1) {
            current = 1;
        }
        // 页码不能超过最大值
        if (current > pageProperties.getMaxPage()) {
            throw new BizException(ErrorCode.PAGE_TOO_DEEP,
                    "页码不能超过" + pageProperties.getMaxPage() + "，请使用条件筛选");
        }
        // 每页大小限制
        if (size < 1) {
            size = pageProperties.getDefaultSize();
        }
        if (size > pageProperties.getMaxSize()) {
            size = pageProperties.getMaxSize();
        }
        return new Page<>(current, size);
    }

    /**
     * 深度分页优化：大数据量时，如果用户翻到很深的页面，提示优化查询条件
     *
     * @param current 当前页码
     * @param total   总记录数
     */
    public void checkDeepPage(long current, long total) {
        if (current > pageProperties.getDeepPageThreshold() && total > 10000) {
            log.warn("Deep page query detected: page={}, total={}, suggest using filter conditions",
                    current, total);
        }
    }

    /**
     * 获取count缓存
     *
     * @param cacheKey 缓存key
     * @return 缓存的count值，如果不存在则返回null
     */
    public Long getCountCache(String cacheKey) {
        if (!pageProperties.isCountCacheEnabled()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get("page:count:" + cacheKey);
            return value != null ? Long.parseLong(value) : null;
        } catch (Exception e) {
            log.warn("Failed to get count cache: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置count缓存
     *
     * @param cacheKey 缓存key
     * @param count   count值
     */
    public void setCountCache(String cacheKey, long count) {
        if (pageProperties.isCountCacheEnabled()) {
            try {
                redisTemplate.opsForValue().set(
                        "page:count:" + cacheKey,
                        String.valueOf(count),
                        pageProperties.getCountCacheSeconds(),
                        TimeUnit.SECONDS
                );
            } catch (Exception e) {
                log.warn("Failed to set count cache: {}", e.getMessage());
            }
        }
    }
}
