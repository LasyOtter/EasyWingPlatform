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
package com.easywing.platform.gateway.constants;

/**
 * 网关通用常量
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class GatewayConstants {

    private GatewayConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** 毫秒/秒 */
    public static final long MILLIS_PER_SECOND = 1000L;

    /** 令牌桶刷新保护窗口（毫秒） */
    public static final long TOKEN_REFILL_MIN_INTERVAL_MILLIS = 1000L;

    /** 优雅关闭等待时间（秒） */
    public static final long SHUTDOWN_AWAIT_SECONDS = 5L;

    /** JWT 请求头前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 缓存键前缀 */
    public static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    public static final String RATE_LIMIT_FALLBACK_SUFFIX = ":fallback";
    public static final String JWT_BLACKLIST_PREFIX = "auth:blacklist:";
}
