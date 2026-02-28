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
package com.easywing.platform.cache.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * 缓存清理监听器
 * <p>
 * 通过Redis Pub/Sub接收其他节点的缓存清理消息，清理本地缓存
 * 用于保证集群环境下缓存一致性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CacheEvictListener implements MessageListener<String> {

    private final CacheManager localCacheManager;

    @Override
    public void onMessage(CharSequence channel, String message) {
        log.info("Received cache evict message: channel={}, message={}", channel, message);
        
        try {
            processEvictMessage(message);
        } catch (Exception e) {
            log.error("Failed to process cache evict message: {}", message, e);
        }
    }

    /**
     * 处理缓存清理消息
     * <p>
     * 消息格式：
     * <ul>
     *     <li>cacheName:cacheKey - 清理指定key</li>
     *     <li>cacheName:* - 清理整个缓存区域</li>
     * </ul>
     */
    private void processEvictMessage(String message) {
        if (message == null || message.isEmpty()) {
            log.warn("Received empty cache evict message");
            return;
        }
        
        // 解析消息：cacheName:cacheKey 或 cacheName:*
        int colonIndex = message.indexOf(':');
        String cacheName;
        String cacheKey = null;
        
        if (colonIndex > 0) {
            cacheName = message.substring(0, colonIndex);
            cacheKey = message.substring(colonIndex + 1);
        } else {
            cacheName = message;
        }
        
        Cache cache = localCacheManager.getCache(cacheName);
        if (cache == null) {
            log.debug("Cache not found: {}", cacheName);
            return;
        }
        
        // 清理本地缓存
        if (cacheKey == null || "*".equals(cacheKey) || cacheKey.isEmpty()) {
            cache.clear();
            log.debug("Cleared local cache: {}", cacheName);
        } else {
            cache.evict(cacheKey);
            log.debug("Evicted local cache: {}:{}", cacheName, cacheKey);
        }
    }
}
