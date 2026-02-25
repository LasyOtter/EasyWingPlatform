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
package com.easywing.platform.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "easywing.security")
public class EasyWingSecurityProperties {

    /**
     * 是否启用安全
     */
    private boolean enabled = true;

    /**
     * OAuth2资源服务器配置
     */
    private OAuth2ResourceServer oauth2 = new OAuth2ResourceServer();

    /**
     * 公共路径（不需要认证）
     */
    private List<String> publicPaths = new ArrayList<>();

    /**
     * 忽略路径（不需要安全过滤）
     */
    private List<String> ignorePaths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OAuth2ResourceServer getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2ResourceServer oauth2) {
        this.oauth2 = oauth2;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getIgnorePaths() {
        return ignorePaths;
    }

    public void setIgnorePaths(List<String> ignorePaths) {
        this.ignorePaths = ignorePaths;
    }

    /**
     * OAuth2资源服务器配置
     */
    public static class OAuth2ResourceServer {

        /**
         * JWT配置
         */
        private JwtConfig jwt = new JwtConfig();

        /**
         * 不透明令牌配置
         */
        private OpaqueTokenConfig opaqueToken = new OpaqueTokenConfig();

        public JwtConfig getJwt() {
            return jwt;
        }

        public void setJwt(JwtConfig jwt) {
            this.jwt = jwt;
        }

        public OpaqueTokenConfig getOpaqueToken() {
            return opaqueToken;
        }

        public void setOpaqueToken(OpaqueTokenConfig opaqueToken) {
            this.opaqueToken = opaqueToken;
        }
    }

    /**
     * JWT配置
     */
    public static class JwtConfig {

        /**
         * JWK Set URI
         */
        private String jwkSetUri;

        /**
         * JWTIssuer URI
         */
        private String issuerUri;

        /**
         * 公钥位置
         */
        private String publicKeyLocation;

        /**
         * 权限声明名称
         */
        private String authoritiesClaimName = "roles";

        /**
         * 用户ID声明名称
         */
        private String userIdClaimName = "sub";

        /**
         * 用户名声明名称
         */
        private String usernameClaimName = "preferred_username";

        /**
         * 租户ID声明名称
         */
        private String tenantIdClaimName = "tenant_id";

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }

        public String getAuthoritiesClaimName() {
            return authoritiesClaimName;
        }

        public void setAuthoritiesClaimName(String authoritiesClaimName) {
            this.authoritiesClaimName = authoritiesClaimName;
        }

        public String getUserIdClaimName() {
            return userIdClaimName;
        }

        public void setUserIdClaimName(String userIdClaimName) {
            this.userIdClaimName = userIdClaimName;
        }

        public String getUsernameClaimName() {
            return usernameClaimName;
        }

        public void setUsernameClaimName(String usernameClaimName) {
            this.usernameClaimName = usernameClaimName;
        }

        public String getTenantIdClaimName() {
            return tenantIdClaimName;
        }

        public void setTenantIdClaimName(String tenantIdClaimName) {
            this.tenantIdClaimName = tenantIdClaimName;
        }
    }

    /**
     * 不透明令牌配置
     */
    public static class OpaqueTokenConfig {

        /**
         * 是否启用不透明令牌
         */
        private boolean enabled = false;

        /**
         * 自省端点URI
         */
        private String introspectionUri;

        /**
         * 客户端ID
         */
        private String clientId;

        /**
         * 客户端密钥
         */
        private String clientSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIntrospectionUri() {
            return introspectionUri;
        }

        public void setIntrospectionUri(String introspectionUri) {
            this.introspectionUri = introspectionUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
