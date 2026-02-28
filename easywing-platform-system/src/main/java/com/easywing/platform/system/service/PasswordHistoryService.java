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
package com.easywing.platform.system.service;

import com.easywing.platform.system.config.UserProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码历史服务
 * <p>
 * 用于检查密码是否在近期使用过，防止用户重复使用旧密码
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final StringRedisTemplate redisTemplate;
    private final UserProperties userProperties;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD_HISTORY_KEY = "user:pwd:history:%s";

    /**
     * 检查密码是否在近期使用过
     *
     * @param userId      用户ID
     * @param newPassword 新密码（明文）
     * @return 如果近期使用过返回true，否则返回false
     */
    public boolean isUsedRecently(Long userId, String newPassword) {
        String key = String.format(PASSWORD_HISTORY_KEY, userId);
        List<String> recentPasswords = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(recentPasswords)) {
            return false;
        }
        return recentPasswords.stream()
                .anyMatch(oldHash -> passwordEncoder.matches(newPassword, oldHash));
    }

    /**
     * 记录密码到历史记录
     *
     * @param userId          用户ID
     * @param encodedPassword 编码后的密码
     */
    public void recordPassword(Long userId, String encodedPassword) {
        String key = String.format(PASSWORD_HISTORY_KEY, userId);
        redisTemplate.opsForList().leftPush(key, encodedPassword);
        redisTemplate.opsForList().trim(key, 0, userProperties.getPasswordHistoryCount() - 1);
        redisTemplate.expire(key, userProperties.getPasswordExpireDays(), TimeUnit.DAYS);
    }
}
