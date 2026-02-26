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
package com.easywing.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证服务配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "easywing.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public static class Jwt {

        private String issuer = "https://auth.easywing.com";

        private Duration accessTokenTtl = Duration.ofMinutes(30);

        private Duration refreshTokenTtl = Duration.ofDays(7);

        private String keyId = "easywing-key-1";

        private String rolesClaimName = "roles";

        private String tenantIdClaimName = "tenant_id";

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getRolesClaimName() {
            return rolesClaimName;
        }

        public void setRolesClaimName(String rolesClaimName) {
            this.rolesClaimName = rolesClaimName;
        }

        public String getTenantIdClaimName() {
            return tenantIdClaimName;
        }

        public void setTenantIdClaimName(String tenantIdClaimName) {
            this.tenantIdClaimName = tenantIdClaimName;
        }
    }
}
