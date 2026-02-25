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
 * 滑动窗口限流Lua脚本
 * <p>
 * 实现精确的滑动窗口限流算法
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class SlidingWindowRateLimiter implements RedisScript<Long> {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            -- 清理过期数据
            local clearBefore = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            
            -- 获取当前窗口内的请求数
            local count = redis.call('ZCARD', key)
            
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                redis.call('PEXPIRE', key, window * 1000 + 1000)
                return limit - count - 1
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