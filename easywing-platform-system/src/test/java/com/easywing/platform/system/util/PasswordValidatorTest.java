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
package com.easywing.platform.system.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码强度校验工具类单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class PasswordValidatorTest {

    private final PasswordValidator passwordValidator = new PasswordValidator();

    @Test
    @DisplayName("Strong password - should pass validation")
    void testStrongPassword() {
        assertTrue(passwordValidator.isStrong("Abcdef1@"));
        assertTrue(passwordValidator.isStrong("Password123!"));
        assertTrue(passwordValidator.isStrong("StrongP@ssw0rd"));
        assertTrue(passwordValidator.isStrong("Test123$"));
    }

    @Test
    @DisplayName("Weak password - missing uppercase letter")
    void testWeakPasswordNoUppercase() {
        assertFalse(passwordValidator.isStrong("abcdef1@"));
        assertFalse(passwordValidator.isStrong("password123!"));
    }

    @Test
    @DisplayName("Weak password - missing lowercase letter")
    void testWeakPasswordNoLowercase() {
        assertFalse(passwordValidator.isStrong("ABCDEF1@"));
        assertFalse(passwordValidator.isStrong("PASSWORD123!"));
    }

    @Test
    @DisplayName("Weak password - missing digit")
    void testWeakPasswordNoDigit() {
        assertFalse(passwordValidator.isStrong("Abcdefg@"));
        assertFalse(passwordValidator.isStrong("Password!"));
    }

    @Test
    @DisplayName("Weak password - missing special character")
    void testWeakPasswordNoSpecialChar() {
        assertFalse(passwordValidator.isStrong("Abcdef12"));
        assertFalse(passwordValidator.isStrong("Password123"));
    }

    @Test
    @DisplayName("Weak password - too short")
    void testWeakPasswordTooShort() {
        assertFalse(passwordValidator.isStrong("Ab1@"));
        assertFalse(passwordValidator.isStrong("Aa1@"));
        assertFalse(passwordValidator.isStrong("Test1@"));
    }

    @Test
    @DisplayName("Null password - should fail validation")
    void testNullPassword() {
        assertFalse(passwordValidator.isStrong(null));
    }

    @Test
    @DisplayName("Empty password - should fail validation")
    void testEmptyPassword() {
        assertFalse(passwordValidator.isStrong(""));
        assertFalse(passwordValidator.isStrong("   "));
    }

    @Test
    @DisplayName("Password with custom minimum length - should validate correctly")
    void testCustomMinLength() {
        assertTrue(passwordValidator.isStrong("Abcdef1@", 8));
        assertTrue(passwordValidator.isStrong("Abcdef1@", 6));
        assertFalse(passwordValidator.isStrong("Ab1@", 10));
    }

    @Test
    @DisplayName("Get strength description - should return correct message")
    void testGetStrengthDescription() {
        String description = passwordValidator.getStrengthDescription();
        assertNotNull(description);
        assertTrue(description.contains("8"));
        assertTrue(description.contains("大小写字母"));
        assertTrue(description.contains("数字"));
        assertTrue(description.contains("特殊字符"));
    }

    @Test
    @DisplayName("Get strength description with custom length - should return correct message")
    void testGetStrengthDescriptionCustomLength() {
        String description = passwordValidator.getStrengthDescription(10);
        assertNotNull(description);
        assertTrue(description.contains("10"));
    }

    @Test
    @DisplayName("Allowed special characters - should pass validation")
    void testAllowedSpecialCharacters() {
        assertTrue(passwordValidator.isStrong("Abcdef1@"));
        assertTrue(passwordValidator.isStrong("Abcdef1$"));
        assertTrue(passwordValidator.isStrong("Abcdef1!"));
        assertTrue(passwordValidator.isStrong("Abcdef1%"));
        assertTrue(passwordValidator.isStrong("Abcdef1*"));
        assertTrue(passwordValidator.isStrong("Abcdef1?"));
        assertTrue(passwordValidator.isStrong("Abcdef1&"));
    }

    @Test
    @DisplayName("Disallowed special characters - should fail validation")
    void testDisallowedSpecialCharacters() {
        assertFalse(passwordValidator.isStrong("Abcdef1#"));
        assertFalse(passwordValidator.isStrong("Abcdef1^"));
        assertFalse(passwordValidator.isStrong("Abcdef1("));
        assertFalse(passwordValidator.isStrong("Abcdef1)"));
    }
}
