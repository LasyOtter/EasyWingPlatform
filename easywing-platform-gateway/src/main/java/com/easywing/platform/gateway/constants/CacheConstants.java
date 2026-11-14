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

import java.time.Duration;

/**
 * 网关缓存配置常量
 * <p>
 * 集中管理所有缓存相关的配置常量，便于统一调整和维护
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class CacheConstants {

    private CacheConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * JWT 缓存配置
     */
    public static final class Jwt {
        private Jwt() {}

        /** JWT 缓存最大数量 */
        public static final int CACHE_MAX_SIZE = 10000;

        /** JWT 缓存过期时间 */
        public static final Duration CACHE_TTL = Duration.ofMinutes(5);

        /** JWK 缓存最大数量 */
        public static final int JWK_CACHE_SIZE = 100;

        /** JWK 缓存过期时间 */
        public static final Duration JWK_CACHE_TTL = Duration.ofHours(1);

        /** 黑名单缓存最大数量 */
        public static final int BLACKLIST_CACHE_SIZE = 10000;

        /** 黑名单缓存过期时间 */
        public static final Duration BLACKLIST_CACHE_TTL = Duration.ofMinutes(5);
    }

    /**
     * 限流缓存配置
     */
    public static final class RateLimit {
        private RateLimit() {}

        /** 本地令牌桶缓存大小 */
        public static final int LOCAL_CACHE_SIZE = 1000;

        /** 本地令牌桶缓存过期时间 */
        public static final Duration LOCAL_CACHE_TTL = Duration.ofMinutes(10);

        /** 默认限流速率（请求/秒） */
        public static final int DEFAULT_RATE = 100;

        /** 默认令牌桶容量 */
        public static final int DEFAULT_CAPACITY = 1000;

        /** 降级模式限流速率 */
        public static final int FALLBACK_RATE = 50;
    }

    /**
     * 灰度发布缓存配置
     */
    public static final class Gray {
        private Gray() {}

        /** 用户版本映射缓存大小 */
        public static final int USER_VERSION_CACHE_SIZE = 10000;

        /** 用户版本映射缓存过期时间 */
        public static final Duration USER_VERSION_CACHE_TTL = Duration.ofHours(1);

        /** 默认权重总和 */
        public static final int DEFAULT_TOTAL_WEIGHT = 100;
    }

    /**
     * 通用缓存配置
     */
    public static final class Common {
        private Common() {}

        /** 小缓存默认大小 */
        public static final int SMALL_CACHE_SIZE = 100;

        /** 中等缓存默认大小 */
        public static final int MEDIUM_CACHE_SIZE = 1000;

        /** 大缓存默认大小 */
        public static final int LARGE_CACHE_SIZE = 10000;

        /** 短期缓存过期时间 */
        public static final Duration SHORT_TTL = Duration.ofMinutes(5);

        /** 中期缓存过期时间 */
        public static final Duration MEDIUM_TTL = Duration.ofHours(1);

        /** 长期缓存过期时间 */
        public static final Duration LONG_TTL = Duration.ofHours(24);
    }
}
