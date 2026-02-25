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

import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis限流Lua脚本
 * <p>
 * 使用令牌桶算法实现分布式限流，保证原子性
 * <p>
 * 返回值：
 * <ul>
 *     <li>>=0: 剩余令牌数</li>
 *     <li>-1: 限流失败</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class RedisRateLimiter implements RedisScript<Long> {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            
            -- 获取当前令牌数和上次刷新时间
            local info = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(info[1])
            local lastRefill = tonumber(info[2])
            
            -- 初始化
            if tokens == nil then
                tokens = capacity
                lastRefill = now
            end
            
            -- 计算新令牌
            local elapsed = math.max(0, now - lastRefill)
            local newTokens = math.floor(elapsed * rate / 1000)
            
            if newTokens > 0 then
                tokens = math.min(capacity, tokens + newTokens)
                lastRefill = now
            end
            
            -- 尝试消费令牌
            if tokens >= requested then
                tokens = tokens - requested
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
                redis.call('PEXPIRE', key, math.floor(capacity / rate * 2000))
                return tokens
            end
            
            return -1
            """;

    @Override
    public String getSha1() {
        return null;
    }

    @Override
    public Class<Long> getResultType() {
        return Long.class;
    }

    @Override
    public String getScriptAsString() {
        return SCRIPT;
    }
}