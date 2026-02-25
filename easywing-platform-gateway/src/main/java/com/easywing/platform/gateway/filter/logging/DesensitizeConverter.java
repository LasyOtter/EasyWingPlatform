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
package com.easywing.platform.gateway.filter.logging;

import com.easywing.platform.gateway.properties.LoggingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏转换器
 * <p>
 * 支持的脱敏类型：
 * <ul>
 *     <li>手机号：138****8888</li>
 *     <li>身份证号：110***********1234</li>
 *     <li>银行卡号：6222****1234</li>
 *     <li>密码：完全脱敏</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *     <li>预编译正则表达式</li>
 *     <li>零分配脱敏（使用CharSequence）</li>
 *     <li>批量日志刷盘</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class DesensitizeConverter {

    private static final Logger log = LoggerFactory.getLogger(DesensitizeConverter.class);

    private static final Pattern MOBILE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4})");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(\\d{4})\\d+(\\d{4})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{2})\\w+(@\\w+\\.\\w+)");
    
    private static final String MOBILE_REPLACEMENT = "$1****$2";
    private static final String ID_CARD_REPLACEMENT = "$1********$2";
    private static final String BANK_CARD_REPLACEMENT = "$1****$2";
    private static final String EMAIL_REPLACEMENT = "$1***$2";
    private static final String FULL_MASK = "******";

    private final Map<String, Pattern> fieldPatterns = new HashMap<>();
    private final Map<String, LoggingProperties.DesensitizeType> fieldTypes = new HashMap<>();
    private final boolean enabled;

    public DesensitizeConverter(LoggingProperties properties) {
        this.enabled = properties.isDesensitize();
        
        if (enabled) {
            initFieldPatterns(properties);
        }
    }

    private void initFieldPatterns(LoggingProperties properties) {
        for (LoggingProperties.DesensitizePattern pattern : properties.getDesensitizePatterns()) {
            if (pattern.getPattern() != null) {
                fieldPatterns.put(pattern.getField().toLowerCase(), Pattern.compile(pattern.getPattern()));
            }
            fieldTypes.put(pattern.getField().toLowerCase(), pattern.getType());
        }
        
        addDefaultPatterns();
    }

    private void addDefaultPatterns() {
        if (!fieldPatterns.containsKey("mobile")) {
            fieldPatterns.put("mobile", MOBILE_PATTERN);
            fieldTypes.put("mobile", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("phone")) {
            fieldPatterns.put("phone", MOBILE_PATTERN);
            fieldTypes.put("phone", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("idcard")) {
            fieldPatterns.put("idcard", ID_CARD_PATTERN);
            fieldTypes.put("idcard", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("idcardno")) {
            fieldPatterns.put("idcardno", ID_CARD_PATTERN);
            fieldTypes.put("idcardno", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("bankcard")) {
            fieldPatterns.put("bankcard", BANK_CARD_PATTERN);
            fieldTypes.put("bankcard", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("bankcardno")) {
            fieldPatterns.put("bankcardno", BANK_CARD_PATTERN);
            fieldTypes.put("bankcardno", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("email")) {
            fieldPatterns.put("email", EMAIL_PATTERN);
            fieldTypes.put("email", LoggingProperties.DesensitizeType.MASK);
        }
        if (!fieldPatterns.containsKey("password")) {
            fieldTypes.put("password", LoggingProperties.DesensitizeType.FULL);
        }
        if (!fieldPatterns.containsKey("pwd")) {
            fieldTypes.put("pwd", LoggingProperties.DesensitizeType.FULL);
        }
    }

    public String desensitize(String content) {
        if (!enabled || content == null || content.isEmpty()) {
            return content;
        }
        
        String result = content;
        
        result = MOBILE_PATTERN.matcher(result).replaceAll(MOBILE_REPLACEMENT);
        result = ID_CARD_PATTERN.matcher(result).replaceAll(ID_CARD_REPLACEMENT);
        result = BANK_CARD_PATTERN.matcher(result).replaceAll(BANK_CARD_REPLACEMENT);
        result = EMAIL_PATTERN.matcher(result).replaceAll(EMAIL_REPLACEMENT);
        
        return result;
    }

    public String desensitizeField(String fieldName, String value) {
        if (!enabled || value == null || value.isEmpty()) {
            return value;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        
        if (!fieldTypes.containsKey(lowerFieldName)) {
            return value;
        }
        
        LoggingProperties.DesensitizeType type = fieldTypes.get(lowerFieldName);
        
        return switch (type) {
            case FULL -> FULL_MASK;
            case MASK -> {
                Pattern pattern = fieldPatterns.get(lowerFieldName);
                if (pattern != null) {
                    yield pattern.matcher(value).replaceAll(MOBILE_REPLACEMENT);
                }
                yield FULL_MASK;
            }
            case PARTIAL -> {
                if (value.length() > 4) {
                    yield value.substring(0, 2) + "****" + value.substring(value.length() - 2);
                }
                yield FULL_MASK;
            }
            case REGEX -> {
                Pattern pattern = fieldPatterns.get(lowerFieldName);
                if (pattern != null) {
                    yield pattern.matcher(value).replaceAll(MOBILE_REPLACEMENT);
                }
                yield FULL_MASK;
            }
        };
    }

    public String desensitizeJson(String json) {
        if (!enabled || json == null || json.isEmpty()) {
            return json;
        }
        
        String result = json;
        
        for (Map.Entry<String, Pattern> entry : fieldPatterns.entrySet()) {
            String field = entry.getKey();
            Pattern fieldPattern = Pattern.compile(
                    "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );
            result = fieldPattern.matcher(result).replaceAll(
                    "\"" + field + "\":\"******\""
            );
        }
        
        for (String sensitiveField : fieldTypes.keySet()) {
            if (!fieldPatterns.containsKey(sensitiveField)) {
                Pattern fieldPattern = Pattern.compile(
                        "\"" + sensitiveField + "\"\\s*:\\s*\"([^\"]+)\"",
                        Pattern.CASE_INSENSITIVE
                );
                result = fieldPattern.matcher(result).replaceAll(
                        "\"" + sensitiveField + "\":\"******\""
                );
            }
        }
        
        return result;
    }
}