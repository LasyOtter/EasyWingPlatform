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
package com.easywing.platform.system.service;

import com.easywing.platform.system.config.UserProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 密码历史服务单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private UserProperties userProperties;
    private PasswordEncoder passwordEncoder;
    private PasswordHistoryService passwordHistoryService;

    @BeforeEach
    void setUp() {
        userProperties = new UserProperties();
        userProperties.setPasswordHistoryCount(5);
        userProperties.setPasswordExpireDays(90);

        passwordEncoder = new BCryptPasswordEncoder();

        when(redisTemplate.opsForList()).thenReturn(listOperations);

        passwordHistoryService = new PasswordHistoryService(redisTemplate, userProperties, passwordEncoder);
    }

    @Test
    @DisplayName("isUsedRecently - no history should return false")
    void testIsUsedRecentlyNoHistory() {
        Long userId = 1L;
        String newPassword = "NewPassword1@";

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(Collections.emptyList());

        boolean result = passwordHistoryService.isUsedRecently(userId, newPassword);

        assertFalse(result);
        verify(listOperations).range("user:pwd:history:1", 0L, -1L);
    }

    @Test
    @DisplayName("isUsedRecently - null history should return false")
    void testIsUsedRecentlyNullHistory() {
        Long userId = 1L;
        String newPassword = "NewPassword1@";

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(null);

        boolean result = passwordHistoryService.isUsedRecently(userId, newPassword);

        assertFalse(result);
    }

    @Test
    @DisplayName("isUsedRecently - password not in history should return false")
    void testIsUsedRecentlyPasswordNotInHistory() {
        Long userId = 1L;
        String newPassword = "NewPassword1@";
        String oldPasswordHash = passwordEncoder.encode("OldPassword1@");

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of(oldPasswordHash));

        boolean result = passwordHistoryService.isUsedRecently(userId, newPassword);

        assertFalse(result);
    }

    @Test
    @DisplayName("isUsedRecently - password in history should return true")
    void testIsUsedRecentlyPasswordInHistory() {
        Long userId = 1L;
        String oldPassword = "OldPassword1@";
        String oldPasswordHash = passwordEncoder.encode(oldPassword);

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of(oldPasswordHash));

        boolean result = passwordHistoryService.isUsedRecently(userId, oldPassword);

        assertTrue(result);
    }

    @Test
    @DisplayName("isUsedRecently - multiple passwords in history")
    void testIsUsedRecentlyMultiplePasswords() {
        Long userId = 1L;
        String password1 = "Password1@";
        String password2 = "Password2@";
        String password3 = "Password3@";
        String newPassword = "Password2@";

        String hash1 = passwordEncoder.encode(password1);
        String hash2 = passwordEncoder.encode(password2);
        String hash3 = passwordEncoder.encode(password3);

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of(hash1, hash2, hash3));

        boolean result = passwordHistoryService.isUsedRecently(userId, newPassword);

        assertTrue(result);
    }

    @Test
    @DisplayName("recordPassword - should store password and trim history")
    void testRecordPassword() {
        Long userId = 1L;
        String encodedPassword = passwordEncoder.encode("NewPassword1@");

        passwordHistoryService.recordPassword(userId, encodedPassword);

        verify(listOperations).leftPush("user:pwd:history:1", encodedPassword);
        verify(listOperations).trim("user:pwd:history:1", 0, userProperties.getPasswordHistoryCount() - 1);
        verify(redisTemplate).expire("user:pwd:history:1", 90L, java.util.concurrent.TimeUnit.DAYS);
    }

    @Test
    @DisplayName("recordPassword - should use correct key format")
    void testRecordPasswordKeyFormat() {
        Long userId = 123L;
        String encodedPassword = "encodedPassword";

        passwordHistoryService.recordPassword(userId, encodedPassword);

        verify(listOperations).leftPush("user:pwd:history:123", encodedPassword);
    }

    @Test
    @DisplayName("isUsedRecently - should use correct key format")
    void testIsUsedRecentlyKeyFormat() {
        Long userId = 456L;
        String newPassword = "NewPassword1@";

        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(Collections.emptyList());

        passwordHistoryService.isUsedRecently(userId, newPassword);

        verify(listOperations).range("user:pwd:history:456", 0L, -1L);
    }

    @Test
    @DisplayName("Password history count configuration")
    void testPasswordHistoryCountConfig() {
        UserProperties customProperties = new UserProperties();
        customProperties.setPasswordHistoryCount(10);
        customProperties.setPasswordExpireDays(30);

        PasswordHistoryService customService = new PasswordHistoryService(redisTemplate, customProperties, passwordEncoder);
        String encodedPassword = "encodedPassword";

        customService.recordPassword(1L, encodedPassword);

        verify(listOperations).trim(anyString(), eq(0L), eq(9L));
        verify(redisTemplate).expire(anyString(), eq(30L), any());
    }
}
