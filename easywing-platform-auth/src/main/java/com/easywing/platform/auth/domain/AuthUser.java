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

    private String userId;
    private String username;
    private String passwordHash;
    private List<String> roles;
    private String tenantId;
    private boolean enabled;

    public AuthUser(String userId, String username, String passwordHash, List<String> roles, String tenantId) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles != null ? List.copyOf(roles) : List.of();
        this.tenantId = tenantId;
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

    public boolean isEnabled() {
        return enabled;
    }
}
