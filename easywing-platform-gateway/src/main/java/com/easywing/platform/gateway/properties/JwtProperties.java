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
package com.easywing.platform.gateway.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT校验配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class JwtProperties {

    private boolean enabled = true;
    private String issuer;
    private String jwkSetUri;
    private Duration cacheTtl = Duration.ofMinutes(5);
    private int cacheMaxSize = 10000;
    private Duration jwkRefreshInterval = Duration.ofMinutes(30);
    private List<String> ignorePaths = new ArrayList<>();
    private List<IssuerConfig> issuers = new ArrayList<>();
    private String userIdClaimName = "sub";
    private String usernameClaimName = "preferred_username";
    private String rolesClaimName = "roles";
    private String tenantIdClaimName = "tenant_id";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public Duration getJwkRefreshInterval() {
        return jwkRefreshInterval;
    }

    public void setJwkRefreshInterval(Duration jwkRefreshInterval) {
        this.jwkRefreshInterval = jwkRefreshInterval;
    }

    public List<String> getIgnorePaths() {
        return ignorePaths;
    }

    public void setIgnorePaths(List<String> ignorePaths) {
        this.ignorePaths = ignorePaths;
    }

    public List<IssuerConfig> getIssuers() {
        return issuers;
    }

    public void setIssuers(List<IssuerConfig> issuers) {
        this.issuers = issuers;
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

    public static class IssuerConfig {
        private String name;
        private String issuer;
        private String jwkSetUri;
        private String audience;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
