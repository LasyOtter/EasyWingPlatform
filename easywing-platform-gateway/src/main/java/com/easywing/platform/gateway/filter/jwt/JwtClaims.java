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
package com.easywing.platform.gateway.filter.jwt;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JWT解析后的Claims对象
 * <p>
 * 用于缓存JWT解析结果，避免重复解析
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class JwtClaims implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String subject;
    private final String username;
    private final String issuer;
    private final List<String> roles;
    private final String tenantId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Map<String, Object> additionalClaims;

    public JwtClaims(String subject, String username, String issuer,
                     List<String> roles, String tenantId,
                     Instant issuedAt, Instant expiresAt,
                     Map<String, Object> additionalClaims) {
        this.subject = subject;
        this.username = username;
        this.issuer = issuer;
        this.roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
        this.tenantId = tenantId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.additionalClaims = additionalClaims != null ? Map.copyOf(additionalClaims) : Collections.emptyMap();
    }

    public String getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public String getIssuer() {
        return issuer;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getAdditionalClaims() {
        return additionalClaims;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public Object getClaim(String name) {
        return additionalClaims.get(name);
    }

    @Override
    public String toString() {
        return "JwtClaims{" +
                "subject='" + subject + '\'' +
                ", username='" + username + '\'' +
                ", issuer='" + issuer + '\'' +
                ", roles=" + roles +
                ", tenantId='" + tenantId + '\'' +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}