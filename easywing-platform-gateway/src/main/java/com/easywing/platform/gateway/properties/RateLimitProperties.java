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
 * 限流配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class RateLimitProperties {

    private boolean enabled = true;
    private int defaultRate = 100;
    private int defaultCapacity = 200;
    private int localCacheSize = 1000;
    private Algorithm algorithm = Algorithm.TOKEN_BUCKET;
    private List<RuleConfig> rules = new ArrayList<>();
    private boolean enableFallback = true;
    private int fallbackRate = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultRate() {
        return defaultRate;
    }

    public void setDefaultRate(int defaultRate) {
        this.defaultRate = defaultRate;
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public int getLocalCacheSize() {
        return localCacheSize;
    }

    public void setLocalCacheSize(int localCacheSize) {
        this.localCacheSize = localCacheSize;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    public boolean isEnableFallback() {
        return enableFallback;
    }

    public void setEnableFallback(boolean enableFallback) {
        this.enableFallback = enableFallback;
    }

    public int getFallbackRate() {
        return fallbackRate;
    }

    public void setFallbackRate(int fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    public enum Algorithm {
        TOKEN_BUCKET,
        LEAKY_BUCKET,
        SLIDING_WINDOW
    }

    public static class RuleConfig {
        private String id;
        private String keyType;
        private String pattern;
        private int rate;
        private int capacity;
        private int requestedTokens = 1;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKeyType() {
            return keyType;
        }

        public void setKeyType(String keyType) {
            this.keyType = keyType;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public int getRate() {
            return rate;
        }

        public void setRate(int rate) {
            this.rate = rate;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(int requestedTokens) {
            this.requestedTokens = requestedTokens;
        }
    }
}
