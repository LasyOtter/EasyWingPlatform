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
package com.easywing.platform.auth.config;

import com.easywing.platform.auth.store.InMemoryUserStore;
import com.easywing.platform.auth.store.RedisUserStore;
import com.easywing.platform.auth.store.UserStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户存储装配
 * <p>
 * {@code easywing.auth.user-store=memory}（默认，单实例开发）或 {@code redis}（多实例共享）。
 * 未显式配置时回退内存实现，保持既有开箱即用行为。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
public class UserStoreConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "easywing.auth", name = "user-store", havingValue = "redis")
    public UserStore redisUserStore(StringRedisTemplate redisTemplate, PasswordEncoder passwordEncoder) {
        return new RedisUserStore(redisTemplate, passwordEncoder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.auth", name = "user-store", havingValue = "memory", matchIfMissing = true)
    public UserStore inMemoryUserStore(PasswordEncoder passwordEncoder) {
        return new InMemoryUserStore(passwordEncoder);
    }
}
