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
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Redis 用户存储
 * <p>
 * 以 Redis Hash 保存用户、以 {@code auth:user:name:*} / {@code auth:user:email:*} 作为索引，
 * 使多个认证实例共享同一份用户库，避免内存实现重启丢失与实例间不一致。
 * <p>
 * 内置账号只初始化一次（{@code auth:user:seeded} 标记），重复启动不会覆盖已有密码。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class RedisUserStore implements UserStore {

    private static final String USER_KEY_PREFIX = "auth:user:";
    private static final String NAME_INDEX_PREFIX = "auth:user:name:";
    private static final String EMAIL_INDEX_PREFIX = "auth:user:email:";
    private static final String SEED_FLAG = "auth:user:seeded";
    private static final long SEED_FLAG_TTL_DAYS = 3650;

    private static final String F_USER_ID = "userId";
    private static final String F_USERNAME = "username";
    private static final String F_EMAIL = "email";
    private static final String F_PASSWORD_HASH = "passwordHash";
    private static final String F_ROLES = "roles";
    private static final String F_TENANT_ID = "tenantId";
    private static final String F_EMAIL_VERIFIED = "emailVerified";

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    public RedisUserStore(StringRedisTemplate redisTemplate, PasswordEncoder passwordEncoder) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void seedDefaults() {
        Boolean first = redisTemplate.opsForValue().setIfAbsent(SEED_FLAG, "1", Duration.ofDays(SEED_FLAG_TTL_DAYS));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        BuiltinUserSeeder.seed(passwordEncoder, (id, username, passwordHash, roles) -> {
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(NAME_INDEX_PREFIX + username, id))) {
                return;
            }
            AuthUser user = new AuthUser(id, username, passwordHash, roles, "default");
            redisTemplate.opsForHash().putAll(userKey(id), toHash(user));
            log.info("Seeded built-in user '{}' in Redis", username);
        });
    }

    @Override
    public AuthUser loadByUsername(String username) {
        AuthUser user = username == null ? null : findById(resolveId(indexName(username)));
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        return user;
    }

    @Override
    public AuthUser loadById(String userId) {
        AuthUser user = findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        return user;
    }

    @Override
    public AuthUser loadByEmail(String email) {
        return email == null ? null : findById(resolveId(indexEmail(email)));
    }

    @Override
    public boolean validatePassword(AuthUser user, String rawPassword) {
        if (user.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Override
    public AuthUser register(String email, String rawPassword, boolean emailVerified, String tenantId) {
        String normalized = normalize(email);
        String userId = UUID.randomUUID().toString();
        Boolean won = redisTemplate.opsForValue().setIfAbsent(NAME_INDEX_PREFIX + normalized, userId);
        if (!Boolean.TRUE.equals(won)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        AuthUser user = new AuthUser(
                userId,
                normalized,
                passwordEncoder.encode(rawPassword),
                List.of("ROLE_USER"),
                tenantId,
                normalized,
                emailVerified);
        redisTemplate.opsForHash().putAll(userKey(userId), toHash(user));
        redisTemplate.opsForValue().set(EMAIL_INDEX_PREFIX + normalized, userId);
        return user;
    }

    @Override
    public AuthUser markEmailVerified(String userId) {
        AuthUser user = findById(userId);
        if (user == null) {
            return null;
        }
        redisTemplate.opsForHash().put(userKey(userId), F_EMAIL_VERIFIED, "true");
        return withEmailVerified(user, true);
    }

    @Override
    public AuthUser updatePassword(String userId, String rawPassword) {
        AuthUser user = findById(userId);
        if (user == null) {
            return null;
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        redisTemplate.opsForHash().put(userKey(userId), F_PASSWORD_HASH, passwordHash);
        return withPasswordHash(user, passwordHash);
    }

    @Override
    public AuthUser findOrCreateExternal(String username, List<String> roles, String tenantId) {
        String normalized = normalize(username);
        AuthUser existing = findById(resolveId(indexName(normalized)));
        if (existing != null) {
            return existing;
        }
        String userId = UUID.randomUUID().toString();
        Boolean won = redisTemplate.opsForValue().setIfAbsent(NAME_INDEX_PREFIX + normalized, userId);
        if (!Boolean.TRUE.equals(won)) {
            return findById(resolveId(indexName(normalized)));
        }
        AuthUser user = new AuthUser(userId, normalized, null, roles, tenantId);
        redisTemplate.opsForHash().putAll(userKey(userId), toHash(user));
        log.info("Provisioned external user '{}' in Redis (tenant={}, roles={})", normalized, tenantId, roles);
        return user;
    }

    private String indexName(String username) {
        return NAME_INDEX_PREFIX + normalize(username);
    }

    private String indexEmail(String email) {
        return EMAIL_INDEX_PREFIX + normalize(email);
    }

    private String resolveId(String indexKey) {
        return redisTemplate.opsForValue().get(indexKey);
    }

    private String userKey(String userId) {
        return USER_KEY_PREFIX + userId;
    }

    private AuthUser findById(String userId) {
        if (userId == null) {
            return null;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(userKey(userId));
        return entries.isEmpty() ? null : fromHash(entries);
    }

    private Map<String, String> toHash(AuthUser user) {
        return Map.of(
                F_USER_ID,
                user.getUserId(),
                F_USERNAME,
                user.getUsername(),
                F_EMAIL,
                user.getEmail() == null ? "" : user.getEmail(),
                F_PASSWORD_HASH,
                user.getPasswordHash() == null ? "" : user.getPasswordHash(),
                F_ROLES,
                String.join(",", user.getRoles()),
                F_TENANT_ID,
                user.getTenantId() == null ? "" : user.getTenantId(),
                F_EMAIL_VERIFIED,
                Boolean.toString(user.isEmailVerified()));
    }

    private AuthUser fromHash(Map<Object, Object> entries) {
        String email = str(entries, F_EMAIL);
        return new AuthUser(
                str(entries, F_USER_ID),
                str(entries, F_USERNAME),
                hash(entries, F_PASSWORD_HASH),
                roles(entries),
                str(entries, F_TENANT_ID),
                email.isEmpty() ? null : email,
                Boolean.parseBoolean(str(entries, F_EMAIL_VERIFIED)));
    }

    private static String str(Map<Object, Object> entries, String field) {
        Object value = entries.get(field);
        return value == null ? "" : value.toString();
    }

    private static String hash(Map<Object, Object> entries, String field) {
        String value = str(entries, field);
        return value.isEmpty() ? null : value;
    }

    private static List<String> roles(Map<Object, Object> entries) {
        String value = str(entries, F_ROLES);
        return value.isEmpty() ? List.of() : List.of(value.split(","));
    }

    private static AuthUser withEmailVerified(AuthUser user, boolean emailVerified) {
        return new AuthUser(
                user.getUserId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRoles(),
                user.getTenantId(),
                user.getEmail(),
                emailVerified);
    }

    private static AuthUser withPasswordHash(AuthUser user, String passwordHash) {
        return new AuthUser(
                user.getUserId(),
                user.getUsername(),
                passwordHash,
                user.getRoles(),
                user.getTenantId(),
                user.getEmail(),
                user.isEmailVerified());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
