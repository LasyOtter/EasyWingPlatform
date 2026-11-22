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
import com.easywing.platform.auth.store.UserStore;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 用户详情服务
 * <p>
 * 作为 {@link UserStore} 的门面，保持既有调用方（TokenService、LDAP/OAuth2 供应、
 * 邮箱验证与密码恢复）的方法签名不变；实际存储由 {@code easywing.auth.user-store}
 * 选择（{@code memory} 默认 / {@code redis} 多实例共享）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class UserDetailsService {

    private final UserStore userStore;

    public UserDetailsService(UserStore userStore) {
        this.userStore = userStore;
    }

    public AuthUser loadByUsername(String username) {
        return userStore.loadByUsername(username);
    }

    public AuthUser loadById(String userId) {
        return userStore.loadById(userId);
    }

    public boolean validatePassword(AuthUser user, String rawPassword) {
        return userStore.validatePassword(user, rawPassword);
    }

    /**
     * 供应外部身份对应的本地用户（LDAP/OAuth2 登录使用）。
     * <p>
     * 按用户名幂等：已存在则直接复用，避免同一外部身份产生多个本地账号。
     *
     * @param username 外部身份用户名（通常为邮箱或登录名）
     * @param roles 角色列表
     * @param tenantId 租户
     * @return 本地用户
     */
    public AuthUser findOrCreateExternal(String username, List<String> roles, String tenantId) {
        return userStore.findOrCreateExternal(username, roles, tenantId);
    }

    public AuthUser register(String email, String rawPassword) {
        return userStore.register(email, rawPassword, false, "default");
    }

    /**
     * 注册自助用户。
     *
     * @param email 邮箱（同时作为登录名）
     * @param rawPassword 明文密码
     * @param emailVerified 是否已验证邮箱；自助注册默认 false
     * @param tenantId 租户
     */
    public AuthUser register(String email, String rawPassword, boolean emailVerified, String tenantId) {
        return userStore.register(email, rawPassword, emailVerified, tenantId);
    }

    /**
     * 标记邮箱已验证。
     *
     * @return 更新后的用户；用户不存在时返回 null
     */
    public AuthUser markEmailVerified(String userId) {
        return userStore.markEmailVerified(userId);
    }

    /**
     * 更新本地密码（密码重置）。
     *
     * @return 更新后的用户；用户不存在时返回 null
     */
    public AuthUser updatePassword(String userId, String rawPassword) {
        return userStore.updatePassword(userId, rawPassword);
    }

    /**
     * 按邮箱查找用户；未找到返回 null（避免账号枚举）。
     */
    public AuthUser loadByEmail(String email) {
        return userStore.loadByEmail(email);
    }
}
