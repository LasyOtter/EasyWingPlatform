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

import java.util.ArrayList;
import java.util.List;

/**
 * 灰度发布配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class GrayProperties {

    private boolean enabled = true;
    private String defaultVersion = "v1";
    private String headerName = "X-Gray-Version";
    private String cookieName = "gray-version";
    private String parameterName = "grayVersion";
    private Strategy strategy = Strategy.WEIGHT;
    private List<ServiceConfig> services = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public List<ServiceConfig> getServices() {
        return services;
    }

    public void setServices(List<ServiceConfig> services) {
        this.services = services;
    }

    public enum Strategy {
        HEADER,
        COOKIE,
        PARAMETER,
        WEIGHT,
        USER_ID,
        CONSISTENT_HASH
    }

    public static class ServiceConfig {
        private String serviceId;
        private String defaultVersion = "v1";
        private List<VersionConfig> versions = new ArrayList<>();
        private List<RuleConfig> rules = new ArrayList<>();

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getDefaultVersion() {
            return defaultVersion;
        }

        public void setDefaultVersion(String defaultVersion) {
            this.defaultVersion = defaultVersion;
        }

        public List<VersionConfig> getVersions() {
            return versions;
        }

        public void setVersions(List<VersionConfig> versions) {
            this.versions = versions;
        }

        public List<RuleConfig> getRules() {
            return rules;
        }

        public void setRules(List<RuleConfig> rules) {
            this.rules = rules;
        }
    }

    public static class VersionConfig {
        private String version;
        private int weight = 100;
        private String metadataKey = "version";

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        public void setMetadataKey(String metadataKey) {
            this.metadataKey = metadataKey;
        }
    }

    public static class RuleConfig {
        private String type;
        private String matchKey;
        private String matchValue;
        private String targetVersion;
        private int weight = 100;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMatchKey() {
            return matchKey;
        }

        public void setMatchKey(String matchKey) {
            this.matchKey = matchKey;
        }

        public String getMatchValue() {
            return matchValue;
        }

        public void setMatchValue(String matchValue) {
            this.matchValue = matchValue;
        }

        public String getTargetVersion() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion = targetVersion;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }
}
