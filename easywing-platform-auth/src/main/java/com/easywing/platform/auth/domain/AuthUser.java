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
package com.easywing.platform.auth.domain;

import java.util.List;

/**
 * 认证用户领域对象
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class AuthUser {

    private final String userId;
    private final String username;
    private final String passwordHash;
    private final List<String> roles;
    private final String tenantId;
    private final String email;
    private boolean emailVerified;
    private boolean enabled;

    public AuthUser(String userId, String username, String passwordHash, List<String> roles, String tenantId) {
        this(userId, username, passwordHash, roles, tenantId, null, true);
    }

    public AuthUser(
            String userId,
            String username,
            String passwordHash,
            List<String> roles,
            String tenantId,
            String email,
            boolean emailVerified) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles != null ? List.copyOf(roles) : List.of();
        this.tenantId = tenantId;
        this.email = email;
        this.emailVerified = emailVerified;
        this.enabled = true;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getTenantId() {
        return tenantId;
    }

    /**
     * 账号邮箱（自助注册时写入，内置/外部身份用户可能为空）。
     */
    public String getEmail() {
        return email;
    }

    /**
     * 邮箱是否已验证。
     * <p>
     * 内置账号与 LDAP/OAuth2 外部身份默认视为已验证；自助注册默认未验证。
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
