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
 * 日志配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class LoggingProperties {

    private boolean enabled = true;
    private boolean desensitize = true;
    private double sampleRate = 1.0;
    private boolean logRequestBody = false;
    private boolean logResponseBody = false;
    private int maxBodyLength = 4096;
    private List<DesensitizePattern> desensitizePatterns = new ArrayList<>();
    private List<String> sensitiveHeaders = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDesensitize() {
        return desensitize;
    }

    public void setDesensitize(boolean desensitize) {
        this.desensitize = desensitize;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isLogRequestBody() {
        return logRequestBody;
    }

    public void setLogRequestBody(boolean logRequestBody) {
        this.logRequestBody = logRequestBody;
    }

    public boolean isLogResponseBody() {
        return logResponseBody;
    }

    public void setLogResponseBody(boolean logResponseBody) {
        this.logResponseBody = logResponseBody;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    public List<DesensitizePattern> getDesensitizePatterns() {
        return desensitizePatterns;
    }

    public void setDesensitizePatterns(List<DesensitizePattern> desensitizePatterns) {
        this.desensitizePatterns = desensitizePatterns;
    }

    public List<String> getSensitiveHeaders() {
        return sensitiveHeaders;
    }

    public void setSensitiveHeaders(List<String> sensitiveHeaders) {
        this.sensitiveHeaders = sensitiveHeaders;
    }

    public static class DesensitizePattern {
        private String field;
        private DesensitizeType type = DesensitizeType.MASK;
        private String pattern;
        private String replacement;
        private int start = 0;
        private int end = 0;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public DesensitizeType getType() {
            return type;
        }

        public void setType(DesensitizeType type) {
            this.type = type;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }
    }

    public enum DesensitizeType {
        MASK,
        FULL,
        PARTIAL,
        REGEX
    }
}
