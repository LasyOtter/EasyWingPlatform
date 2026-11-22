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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.auth.store.InMemoryUserStore;
import com.easywing.platform.auth.store.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用户详情服务单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class UserDetailsServiceTest {

    private UserDetailsService service;
    private UserStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryUserStore(new BCryptPasswordEncoder());
        service = new UserDetailsService(store);
    }

    @Test
    @DisplayName("自助注册：邮箱规范化为小写、默认未验证、可按邮箱找回")
    void registerNormalizesEmailAndFindsByEmail() {
        var user = service.register("Alice@Example.COM", "a-strong-password");

        assertThat(user.getUsername()).isEqualTo("alice@example.com");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(service.loadByEmail(" ALICE@example.com ")).isSameAs(user);
        assertThat(service.validatePassword(user, "a-strong-password")).isTrue();
    }

    @Test
    @DisplayName("重复邮箱注册被拒")
    void registerRejectsDuplicateEmail() {
        service.register("dup@example.com", "a-strong-password");

        assertThatThrownBy(() -> service.register("DUP@example.com", "another-password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("标记邮箱已验证")
    void markEmailVerifiedFlipsFlag() {
        var user = service.register("v@example.com", "a-strong-password");

        assertThat(service.markEmailVerified(user.getUserId()).isEmailVerified())
                .isTrue();
        assertThat(service.loadByEmail("v@example.com").isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("重置密码后旧密码失效、新密码可用")
    void updatePasswordReplacesCredentials() {
        var user = service.register("p@example.com", "old-password");

        var updated = service.updatePassword(user.getUserId(), "new-password");

        assertThat(service.validatePassword(updated, "new-password")).isTrue();
        assertThat(service.validatePassword(updated, "old-password")).isFalse();
    }

    @Test
    @DisplayName("LDAP/OAuth2 外部身份默认视为已验证邮箱")
    void externalUsersAreEmailVerifiedByDefault() {
        var user = service.findOrCreateExternal("ldap-user", java.util.List.of("ROLE_USER"), "tenant-1");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(service.validatePassword(user, "anything")).isFalse();
    }
}
