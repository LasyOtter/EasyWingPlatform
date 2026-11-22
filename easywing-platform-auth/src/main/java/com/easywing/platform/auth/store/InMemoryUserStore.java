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
package com.easywing.platform.auth.store;

import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 内存用户存储
 * <p>
 * 单实例开发/测试默认实现，进程重启即丢失，不能作为多实例生产用户库。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class InMemoryUserStore implements UserStore {

    private final Map<String, AuthUser> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, AuthUser> usersById = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public InMemoryUserStore(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        seed();
    }

    @Override
    public AuthUser loadByUsername(String username) {
        AuthUser user = username == null ? null : usersByUsername.get(normalize(username));
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        return user;
    }

    @Override
    public AuthUser loadById(String userId) {
        AuthUser user = usersById.get(userId);
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        return user;
    }

    @Override
    public AuthUser loadByEmail(String email) {
        return email == null ? null : usersByUsername.get(normalize(email));
    }

    @Override
    public boolean validatePassword(AuthUser user, String rawPassword) {
        if (user.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Override
    public synchronized AuthUser register(String email, String rawPassword, boolean emailVerified, String tenantId) {
        String normalized = normalize(email);
        if (usersByUsername.containsKey(normalized)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        AuthUser user = new AuthUser(
                UUID.randomUUID().toString(),
                normalized,
                passwordEncoder.encode(rawPassword),
                List.of("ROLE_USER"),
                tenantId,
                normalized,
                emailVerified);
        usersByUsername.put(normalized, user);
        usersById.put(user.getUserId(), user);
        return user;
    }

    @Override
    public synchronized AuthUser markEmailVerified(String userId) {
        AuthUser user = usersById.get(userId);
        if (user == null) {
            return null;
        }
        user.setEmailVerified(true);
        return user;
    }

    @Override
    public synchronized AuthUser updatePassword(String userId, String rawPassword) {
        AuthUser user = usersById.get(userId);
        if (user == null) {
            return null;
        }
        AuthUser updated = new AuthUser(
                user.getUserId(),
                user.getUsername(),
                passwordEncoder.encode(rawPassword),
                user.getRoles(),
                user.getTenantId(),
                user.getEmail(),
                user.isEmailVerified());
        usersByUsername.put(updated.getUsername(), updated);
        usersById.put(userId, updated);
        return updated;
    }

    @Override
    public synchronized AuthUser findOrCreateExternal(String username, List<String> roles, String tenantId) {
        String normalized = normalize(username);
        AuthUser existing = usersByUsername.get(normalized);
        if (existing != null) {
            return existing;
        }
        AuthUser user = new AuthUser(UUID.randomUUID().toString(), normalized, null, roles, tenantId);
        usersByUsername.put(normalized, user);
        usersById.put(user.getUserId(), user);
        log.info("Provisioned external user '{}' (tenant={}, roles={})", normalized, tenantId, roles);
        return user;
    }

    private void seed() {
        BuiltinUserSeeder.seed(passwordEncoder, (id, username, passwordHash, roles) -> {
            AuthUser user = new AuthUser(id, username, passwordHash, roles, "default");
            usersByUsername.put(username, user);
            usersById.put(id, user);
        });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
