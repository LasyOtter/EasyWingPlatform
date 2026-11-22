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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证服务配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "easywing.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();

    private Registration registration = new Registration();

    private Ldap ldap = new Ldap();

    private OAuth2 oauth2 = new OAuth2();

    private Email email = new Email();

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public Ldap getLdap() {
        return ldap;
    }

    public void setLdap(Ldap ldap) {
        this.ldap = ldap;
    }

    public OAuth2 getOAuth2() {
        return oauth2;
    }

    public void setOAuth2(OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    public static class Registration {
        private boolean enabled;

        /**
         * 注册后是否必须完成邮箱验证才能登录。
         * <p>
         * 默认要求验证；仅在明确关闭时允许未验证邮箱直接登录。
         */
        private boolean requireEmailVerification = true;

        private String defaultTenant = "default";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireEmailVerification() {
            return requireEmailVerification;
        }

        public void setRequireEmailVerification(boolean requireEmailVerification) {
            this.requireEmailVerification = requireEmailVerification;
        }

        public String getDefaultTenant() {
            return defaultTenant;
        }

        public void setDefaultTenant(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }
    }

    /**
     * LDAP 绑定认证配置
     * <p>
     * 默认关闭；仅在 {@code easywing.auth.ldap.enabled=true} 时装配 LDAP 目录源与登录端点。
     * 凭据只通过配置/环境变量注入，禁止硬编码。
     */
    public static class Ldap {

        private boolean enabled;

        private String url = "ldap://localhost:389";

        private String baseDn = "";

        private String userSearchBase = "";

        private String userSearchFilter = "(uid={0})";

        private String managerDn;

        private String managerPassword;

        private String defaultTenant = "default";

        private List<String> defaultRoles = List.of("ROLE_USER");

        private String emailAttribute = "mail";

        private String displayNameAttribute = "cn";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBaseDn() {
            return baseDn;
        }

        public void setBaseDn(String baseDn) {
            this.baseDn = baseDn;
        }

        public String getUserSearchBase() {
            return userSearchBase;
        }

        public void setUserSearchBase(String userSearchBase) {
            this.userSearchBase = userSearchBase;
        }

        public String getUserSearchFilter() {
            return userSearchFilter;
        }

        public void setUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
        }

        public String getManagerDn() {
            return managerDn;
        }

        public void setManagerDn(String managerDn) {
            this.managerDn = managerDn;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        public String getDefaultTenant() {
            return defaultTenant;
        }

        public void setDefaultTenant(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }

        public List<String> getDefaultRoles() {
            return defaultRoles;
        }

        public void setDefaultRoles(List<String> defaultRoles) {
            this.defaultRoles = defaultRoles;
        }

        public String getEmailAttribute() {
            return emailAttribute;
        }

        public void setEmailAttribute(String emailAttribute) {
            this.emailAttribute = emailAttribute;
        }

        public String getDisplayNameAttribute() {
            return displayNameAttribute;
        }

        public void setDisplayNameAttribute(String displayNameAttribute) {
            this.displayNameAttribute = displayNameAttribute;
        }
    }

    /**
     * 外部 OAuth2/OIDC 登录配置
     * <p>
     * 默认关闭；开启后由 {@code easywing.auth.oauth2.providers} 动态构建
     * {@code ClientRegistration}，登录成功后映射为本地用户并签发平台 JWT。
     */
    public static class OAuth2 {

        private boolean enabled;

        private String defaultTenant = "default";

        private Map<String, Provider> providers = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultTenant() {
            return defaultTenant;
        }

        public void setDefaultTenant(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }

        public Map<String, Provider> getProviders() {
            return providers;
        }

        public void setProviders(Map<String, Provider> providers) {
            this.providers = providers;
        }

        /**
         * 单个 OAuth2/OIDC 提供方配置。
         * <p>
         * 需显式提供授权/令牌/用户信息/JWKS 端点；未做 issuer 元数据自动发现，
         * 以避免启动期对外网产生强依赖。
         */
        public static class Provider {

            private boolean enabled = true;

            private String clientId;

            private String clientSecret;

            private String issuerUri;

            private String authorizationUri;

            private String tokenUri;

            private String userInfoUri;

            private String jwkSetUri;

            private List<String> scopes = List.of("openid", "profile", "email");

            private String emailAttribute = "email";

            private String displayNameAttribute = "name";

            private String userNameAttributeName = "sub";

            private List<String> roles = List.of("ROLE_USER");

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
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

            public String getIssuerUri() {
                return issuerUri;
            }

            public void setIssuerUri(String issuerUri) {
                this.issuerUri = issuerUri;
            }

            public String getAuthorizationUri() {
                return authorizationUri;
            }

            public void setAuthorizationUri(String authorizationUri) {
                this.authorizationUri = authorizationUri;
            }

            public String getTokenUri() {
                return tokenUri;
            }

            public void setTokenUri(String tokenUri) {
                this.tokenUri = tokenUri;
            }

            public String getUserInfoUri() {
                return userInfoUri;
            }

            public void setUserInfoUri(String userInfoUri) {
                this.userInfoUri = userInfoUri;
            }

            public String getJwkSetUri() {
                return jwkSetUri;
            }

            public void setJwkSetUri(String jwkSetUri) {
                this.jwkSetUri = jwkSetUri;
            }

            public List<String> getScopes() {
                return scopes;
            }

            public void setScopes(List<String> scopes) {
                this.scopes = scopes;
            }

            public String getEmailAttribute() {
                return emailAttribute;
            }

            public void setEmailAttribute(String emailAttribute) {
                this.emailAttribute = emailAttribute;
            }

            public String getDisplayNameAttribute() {
                return displayNameAttribute;
            }

            public void setDisplayNameAttribute(String displayNameAttribute) {
                this.displayNameAttribute = displayNameAttribute;
            }

            public String getUserNameAttributeName() {
                return userNameAttributeName;
            }

            public void setUserNameAttributeName(String userNameAttributeName) {
                this.userNameAttributeName = userNameAttributeName;
            }

            public List<String> getRoles() {
                return roles;
            }

            public void setRoles(List<String> roles) {
                this.roles = roles;
            }
        }
    }

    /**
     * 邮箱验证与密码重置配置
     * <p>
     * 默认 {@code enabled=false}：不接 SMTP 时使用日志通知器，链接只写入日志，
     * 便于本地开发与自动化测试；配置 {@code smtp.host} 后自动切换为真实发送。
     * 凭据只通过配置/环境变量注入，禁止硬编码。
     */
    public static class Email {

        private boolean enabled;

        private String from = "no-reply@easywing.com";

        /** 验证/重置链接的基地址，例如 https://app.example.com */
        private String verifyBaseUrl = "http://localhost:5173";

        private Duration verificationTokenTtl = Duration.ofHours(24);

        private Duration resetTokenTtl = Duration.ofHours(1);

        private String smtpHost;

        private int smtpPort = 587;

        private String smtpUsername;

        private String smtpPassword;

        private boolean smtpStartTls = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getVerifyBaseUrl() {
            return verifyBaseUrl;
        }

        public void setVerifyBaseUrl(String verifyBaseUrl) {
            this.verifyBaseUrl = verifyBaseUrl;
        }

        public Duration getVerificationTokenTtl() {
            return verificationTokenTtl;
        }

        public void setVerificationTokenTtl(Duration verificationTokenTtl) {
            this.verificationTokenTtl = verificationTokenTtl;
        }

        public Duration getResetTokenTtl() {
            return resetTokenTtl;
        }

        public void setResetTokenTtl(Duration resetTokenTtl) {
            this.resetTokenTtl = resetTokenTtl;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public int getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(int smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getSmtpUsername() {
            return smtpUsername;
        }

        public void setSmtpUsername(String smtpUsername) {
            this.smtpUsername = smtpUsername;
        }

        public String getSmtpPassword() {
            return smtpPassword;
        }

        public void setSmtpPassword(String smtpPassword) {
            this.smtpPassword = smtpPassword;
        }

        public boolean isSmtpStartTls() {
            return smtpStartTls;
        }

        public void setSmtpStartTls(boolean smtpStartTls) {
            this.smtpStartTls = smtpStartTls;
        }
    }

    public static class Jwt {

        private String issuer = "https://auth.easywing.com";

        private Duration accessTokenTtl = Duration.ofMinutes(30);

        private Duration refreshTokenTtl = Duration.ofDays(7);

        private String keyId = "easywing-key-1";
        private String keyPath;

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

        /**
         * 密钥对持久化文件路径（PEM 格式，同时包含私钥与公钥块）。
         * <p>
         * 配置后启动时优先从该文件加载；文件不存在则生成并写入。
         * 多实例部署必须配置同一路径，否则各实例密钥不一致会导致验签随机失败。
         */
        public String getKeyPath() {
            return keyPath;
        }

        public void setKeyPath(String keyPath) {
            this.keyPath = keyPath;
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
