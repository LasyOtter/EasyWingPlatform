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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * SecurityUtils 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("获取当前认证信息-成功")
    void getAuthentication_Success() {
        // Given
        Authentication auth = mock(Authentication.class);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Authentication result = SecurityUtils.getAuthentication();

        // Then
        assertThat(result).isEqualTo(auth);
    }

    @Test
    @DisplayName("获取当前用户ID-成功")
    void getCurrentUserId_Success() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        String result = SecurityUtils.getCurrentUserId();

        // Then
        assertThat(result).isEqualTo("123");
    }

    @Test
    @DisplayName("获取当前用户ID-无JWT")
    void getCurrentUserId_NoJwt() {
        // Given - no authentication set

        // When
        String result = SecurityUtils.getCurrentUserId();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取当前用户名-从JWT")
    void getCurrentUsername_FromJwt() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("preferred_username")).thenReturn("testuser");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        String result = SecurityUtils.getCurrentUsername();

        // Then
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    @DisplayName("获取当前用户名-从Authentication")
    void getCurrentUsername_FromAuthentication() {
        // Given
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymous");
        when(auth.getName()).thenReturn("anonymousUser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        String result = SecurityUtils.getCurrentUsername();

        // Then
        assertThat(result).isEqualTo("anonymousUser");
    }

    @Test
    @DisplayName("获取当前用户名-默认system")
    void getCurrentUsername_DefaultSystem() {
        // When
        String result = SecurityUtils.getCurrentUsername();

        // Then
        assertThat(result).isEqualTo("system");
    }

    @Test
    @DisplayName("获取当前部门ID-成功")
    void getCurrentDeptId_Success() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("dept_id")).thenReturn(100L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Long result = SecurityUtils.getCurrentDeptId();

        // Then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("获取当前部门ID-从deptId字段")
    void getCurrentDeptId_FromDeptIdField() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("dept_id")).thenReturn(null);
        when(jwt.getClaim("deptId")).thenReturn(200L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Long result = SecurityUtils.getCurrentDeptId();

        // Then
        assertThat(result).isEqualTo(200L);
    }

    @Test
    @DisplayName("获取当前部门ID-字符串类型")
    void getCurrentDeptId_StringType() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("dept_id")).thenReturn("300");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Long result = SecurityUtils.getCurrentDeptId();

        // Then
        assertThat(result).isEqualTo(300L);
    }

    @Test
    @DisplayName("获取当前部门ID-无效字符串")
    void getCurrentDeptId_InvalidString() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("dept_id")).thenReturn("invalid");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Long result = SecurityUtils.getCurrentDeptId();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取当前租户ID-成功")
    void getCurrentTenantId_Success() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("tenant_id")).thenReturn(1L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Long result = SecurityUtils.getCurrentTenantId();

        // Then
        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("获取当前租户ID-默认值")
    void getCurrentTenantId_Default() {
        // Given - no authentication

        // When
        Long result = SecurityUtils.getCurrentTenantId();

        // Then
        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("是否已认证-已认证")
    void isAuthenticated_True() {
        // Given
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        boolean result = SecurityUtils.isAuthenticated();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("是否已认证-未认证")
    void isAuthenticated_False() {
        // Given - no authentication

        // When
        boolean result = SecurityUtils.isAuthenticated();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("是否超级管理员-是")
    void isAdmin_True() {
        // Given
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn(List.of(authority));

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        boolean result = SecurityUtils.isAdmin();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("是否超级管理员-否")
    void isAdmin_False() {
        // Given
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn(List.of(authority));

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        boolean result = SecurityUtils.isAdmin();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("是否超级管理员-无认证")
    void isAdmin_NoAuth() {
        // Given - no authentication

        // When
        boolean result = SecurityUtils.isAdmin();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("获取JWT-成功")
    void getJwt_Success() {
        // Given
        Jwt jwt = mock(Jwt.class);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Jwt result = SecurityUtils.getJwt();

        // Then
        assertThat(result).isEqualTo(jwt);
    }

    @Test
    @DisplayName("获取JWT-非JWT主体")
    void getJwt_NotJwtPrincipal() {
        // Given
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymous");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        Jwt result = SecurityUtils.getJwt();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取自定义声明-成功")
    void getClaim_Success() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom_claim", String.class)).thenReturn("custom_value");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        String result = SecurityUtils.getClaim("custom_claim", String.class);

        // Then
        assertThat(result).isEqualTo("custom_value");
    }

    @Test
    @DisplayName("获取自定义声明-无JWT")
    void getClaim_NoJwt() {
        // Given - no authentication

        // When
        String result = SecurityUtils.getClaim("custom_claim", String.class);

        // Then
        assertThat(result).isNull();
    }
}
