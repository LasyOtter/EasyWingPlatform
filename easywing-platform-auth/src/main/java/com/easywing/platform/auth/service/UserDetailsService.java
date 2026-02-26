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
package com.easywing.platform.auth.service;

import com.easywing.platform.auth.domain.AuthUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户详情服务
 * <p>
 * 内置内存存储，便于开箱即用和测试。
 * 生产环境可替换为数据库实现（实现相同方法签名即可）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class UserDetailsService {

    private final Map<String, AuthUser> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, AuthUser> usersById = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public UserDetailsService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        initDefaultUsers();
    }

    public AuthUser loadByUsername(String username) {
        AuthUser user = usersByUsername.get(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        return user;
    }

    public AuthUser loadById(String userId) {
        AuthUser user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found by id: " + userId);
        }
        return user;
    }

    public boolean validatePassword(AuthUser user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private void initDefaultUsers() {
        register("1", "admin", "admin123", List.of("ROLE_ADMIN", "ROLE_USER"), "default");
        register("2", "user", "user123", List.of("ROLE_USER"), "default");
    }

    private void register(String id, String username, String rawPassword, List<String> roles, String tenantId) {
        AuthUser user = new AuthUser(id, username, passwordEncoder.encode(rawPassword), roles, tenantId);
        usersByUsername.put(username, user);
        usersById.put(id, user);
    }
}
