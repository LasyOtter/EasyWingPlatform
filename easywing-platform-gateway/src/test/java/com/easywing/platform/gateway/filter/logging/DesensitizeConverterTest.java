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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏转换器测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class DesensitizeConverterTest {

    private DesensitizeConverter converter;
    private LoggingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LoggingProperties();
        properties.setDesensitize(true);
        properties.setDesensitizePatterns(List.of());
        
        converter = new DesensitizeConverter(properties);
    }

    @Test
    @DisplayName("Mobile phone desensitization")
    void testMobilePhoneDesensitization() {
        String result = converter.desensitize("手机号13812345678测试");
        assertEquals("手机号138****5678测试", result);
    }

    @Test
    @DisplayName("ID card desensitization")
    void testIdCardDesensitization() {
        String result = converter.desensitize("身份证号110101199001011234测试");
        assertEquals("身份证号110101********1234测试", result);
    }

    @Test
    @DisplayName("Bank card desensitization")
    void testBankCardDesensitization() {
        String result = converter.desensitize("银行卡号6222021234567890测试");
        assertTrue(result.contains("****"));
    }

    @Test
    @DisplayName("Email desensitization")
    void testEmailDesensitization() {
        String result = converter.desensitize("邮箱testuser@example.com测试");
        assertTrue(result.contains("***"));
    }

    @Test
    @DisplayName("Field desensitization - password should be fully masked")
    void testPasswordFieldDesensitization() {
        String result = converter.desensitizeField("password", "mypassword123");
        assertEquals("******", result);
    }

    @Test
    @DisplayName("Field desensitization - mobile should be partially masked")
    void testMobileFieldDesensitization() {
        String result = converter.desensitizeField("mobile", "13812345678");
        assertTrue(result.contains("****"));
    }

    @Test
    @DisplayName("Field desensitization - non-sensitive field should remain unchanged")
    void testNonSensitiveFieldDesensitization() {
        String result = converter.desensitizeField("username", "testuser");
        assertEquals("testuser", result);
    }

    @Test
    @DisplayName("JSON desensitization")
    void testJsonDesensitization() {
        String json = "{\"username\":\"test\",\"password\":\"secret123\",\"mobile\":\"13812345678\"}";
        String result = converter.desensitizeJson(json);
        
        assertTrue(result.contains("\"password\":\"******\""));
        assertTrue(result.contains("\"mobile\":\"******\""));
        assertTrue(result.contains("\"username\":\"test\""));
    }

    @Test
    @DisplayName("Null value handling")
    void testNullValueHandling() {
        assertNull(converter.desensitize(null));
        assertNull(converter.desensitizeField("password", null));
    }

    @Test
    @DisplayName("Empty string handling")
    void testEmptyStringHandling() {
        assertEquals("", converter.desensitize(""));
        assertEquals("", converter.desensitizeField("password", ""));
    }

    @Test
    @DisplayName("Desensitization disabled")
    void testDesensitizationDisabled() {
        LoggingProperties disabledProps = new LoggingProperties();
        disabledProps.setDesensitize(false);
        DesensitizeConverter disabledConverter = new DesensitizeConverter(disabledProps);
        
        String input = "手机号13812345678测试";
        String result = disabledConverter.desensitize(input);
        
        assertEquals(input, result);
    }

    @Test
    @DisplayName("Complex JSON desensitization")
    void testComplexJsonDesensitization() {
        String json = "{\"user\":{\"name\":\"张三\",\"idCard\":\"110101199001011234\",\"contacts\":[{\"type\":\"mobile\",\"value\":\"13812345678\"}]}}";
        String result = converter.desensitizeJson(json);
        
        assertTrue(result.contains("\"idCard\":\"******\""));
        assertTrue(result.contains("\"name\":\"张三\""));
    }
}
