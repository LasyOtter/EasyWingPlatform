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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.core.exception.BizException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Redis 用户存储单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisUserStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 模拟 Redis 的索引键（姓名/邮箱/播种标记）。 */
    private final Map<String, String> index = new HashMap<>();

    /** 模拟 Redis 的用户哈希，按 {@code auth:user:<id>} 键隔离。 */
    private final Map<String, Map<Object, Object>> users = new HashMap<>();

    private RedisUserStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            if (index.containsKey(key)) {
                return false;
            }
            index.put(key, value);
            return true;
        });
        doAnswer(invocation -> {
                    index.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(valueOperations)
                .set(anyString(), anyString());
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String value = invocation.getArgument(1);
                    if (index.containsKey(key)) {
                        return false;
                    }
                    index.put(key, value);
                    return true;
                });
        when(valueOperations.get(anyString())).thenAnswer(invocation -> index.get(invocation.getArgument(0)));
        when(hashOperations.entries(anyString()))
                .thenAnswer(invocation -> new HashMap<>(users.getOrDefault(invocation.getArgument(0), Map.of())));
        doAnswer(invocation -> {
                    users.put(invocation.getArgument(0), new HashMap<>(invocation.getArgument(1)));
                    return null;
                })
                .when(hashOperations)
                .putAll(anyString(), anyMap());
        doAnswer(invocation -> {
                    users.computeIfAbsent(invocation.getArgument(0), key -> new HashMap<>())
                            .put(invocation.getArgument(1), invocation.getArgument(2));
                    return null;
                })
                .when(hashOperations)
                .put(anyString(), any(), any());
        store = new RedisUserStore(redisTemplate, passwordEncoder);
    }

    @Test
    @DisplayName("注册规范化邮箱、写入姓名与邮箱索引，重复邮箱被拒")
    void registerNormalizesAndIndexes() {
        AuthUser user = store.register("Alice@Example.COM", "a-strong-password", false, "tenant-1");

        assertThat(user.getUsername()).isEqualTo("alice@example.com");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getRoles()).containsExactly("ROLE_USER");
        assertThat(user.getTenantId()).isEqualTo("tenant-1");
        assertThat(store.loadByUsername(" ALICE@example.com ").getUserId()).isEqualTo(user.getUserId());
        assertThat(store.loadByEmail("Alice@example.com").getUserId()).isEqualTo(user.getUserId());

        assertThatThrownBy(() -> store.register("alice@example.com", "other-password", false, "default"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("邮箱验证与密码重置在 Redis 中持久生效")
    void verificationAndPasswordPersist() {
        AuthUser user = store.register("v@example.com", "old-password", false, "default");

        assertThat(store.markEmailVerified(user.getUserId()).isEmailVerified()).isTrue();
        assertThat(store.loadByEmail("v@example.com").isEmailVerified()).isTrue();

        AuthUser rotated = store.updatePassword(user.getUserId(), "new-password");

        assertThat(store.validatePassword(rotated, "new-password")).isTrue();
        assertThat(store.validatePassword(rotated, "old-password")).isFalse();
    }

    @Test
    @DisplayName("外部身份幂等供应，且无本地密码")
    void externalIdentityIsIdempotent() {
        AuthUser first = store.findOrCreateExternal("ldap-user", List.of("ROLE_USER"), "tenant-1");
        AuthUser second = store.findOrCreateExternal("LDAP-USER", List.of("ROLE_ADMIN"), "tenant-2");

        assertThat(second.getUserId()).isEqualTo(first.getUserId());
        assertThat(second.getRoles()).containsExactly("ROLE_USER");
        assertThat(first.getPasswordHash()).isNull();
        assertThat(store.validatePassword(first, "anything")).isFalse();
    }

    @Test
    @DisplayName("外部身份并发冲突时回退到既有账号")
    void externalRaceFallsBackToExisting() {
        store.findOrCreateExternal("racer", List.of("ROLE_USER"), "tenant-1");

        AuthUser second = store.findOrCreateExternal("racer", List.of("ROLE_ADMIN"), "tenant-2");

        assertThat(second.getTenantId()).isEqualTo("tenant-1");
        assertThat(index).containsKey("auth:user:name:racer");
    }

    @Test
    @DisplayName("用户不存在时凭据查询抛统一异常，邮箱查询返回 null")
    void missingUserIsOpaque() {
        assertThatThrownBy(() -> store.loadByUsername("ghost")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> store.loadById("ghost")).isInstanceOf(BizException.class);
        assertThat(store.loadByEmail("ghost@example.com")).isNull();
        assertThat(store.markEmailVerified("ghost")).isNull();
        assertThat(store.updatePassword("ghost", "x")).isNull();
    }

    @Test
    @DisplayName("内置账号只播种一次，重复启动不覆盖既有用户")
    void seedingRunsOnlyOnce() {
        store.seedDefaults();

        assertThat(store.loadByUsername("admin").getRoles()).contains("ROLE_ADMIN", "ROLE_USER");
        assertThat(store.loadByUsername("user").getRoles()).containsExactly("ROLE_USER");

        users.clear();
        store.seedDefaults();

        assertThat(users).isEmpty();
    }

    @Test
    @DisplayName("seeding 后可通过邮箱索引找回（内置账号邮箱为空）")
    void builtinUsersHaveNoEmail() {
        store.seedDefaults();

        assertThat(store.loadByEmail("admin")).isNull();
        assertThat(store.loadByUsername("admin").getEmail()).isNull();
    }

    @Test
    @DisplayName("哈希可还原角色、租户与验证状态")
    void hashRoundTripsFields() {
        AuthUser user = store.register("t@example.com", "pw", true, "tenant-9");

        AuthUser loaded = store.loadById(user.getUserId());

        assertThat(loaded.getEmail()).isEqualTo("t@example.com");
        assertThat(loaded.isEmailVerified()).isTrue();
        assertThat(loaded.getTenantId()).isEqualTo("tenant-9");
        assertThat(loaded.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("seeded 标记已存在时不再写入任何用户")
    void seededFlagShortCircuits() {
        index.put("auth:user:seeded", "1");

        store.seedDefaults();

        assertThat(users).isEmpty();
    }

    @Test
    @DisplayName("未知索引键按凭据错误处理")
    void unknownIndexKeyIsOpaque() {
        when(valueOperations.get(eq("auth:user:name:ghost"))).thenReturn("999");

        assertThatThrownBy(() -> store.loadByUsername("ghost")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("外部身份供应写入租户与角色")
    void externalUserStoresTenantAndRoles() {
        AuthUser user = store.findOrCreateExternal("ext@corp.com", List.of("ROLE_ADMIN"), "acme");

        assertThat(user.getTenantId()).isEqualTo("acme");
        assertThat(user.getUsername()).isEqualTo("ext@corp.com");
        assertThat(user.isEmailVerified()).isTrue();
    }
}
