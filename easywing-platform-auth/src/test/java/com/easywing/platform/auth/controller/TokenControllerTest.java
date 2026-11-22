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
package com.easywing.platform.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.config.AuthSecurityConfig;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.LoginRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.exception.AuthExceptionHandler;
import com.easywing.platform.auth.service.EmailVerificationService;
import com.easywing.platform.auth.service.MachineClientService;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.auth.service.UserDetailsService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 令牌控制器单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@WebMvcTest(TokenController.class)
@Import({AuthSecurityConfig.class, GlobalExceptionHandler.class, AuthExceptionHandler.class})
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private MachineClientService machineClientService;

    @Test
    @DisplayName("Login with valid credentials - should return tokens")
    void testLoginSuccess() throws Exception {
        AuthUser user = new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default");
        TokenResponse tokenResponse = new TokenResponse("access-token", "refresh-token", 1800L);

        when(userDetailsService.loadByUsername("admin")).thenReturn(user);
        when(userDetailsService.validatePassword(any(), any())).thenReturn(true);
        when(tokenService.issueTokenPair(any())).thenReturn(tokenResponse);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        mockMvc.perform(post("/api/v1/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("Login with invalid credentials - should return 401")
    void testLoginInvalidCredentials() throws Exception {
        AuthUser user = new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default");

        when(userDetailsService.loadByUsername("admin")).thenReturn(user);
        when(userDetailsService.validatePassword(any(), any())).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with missing username - should return 400")
    void testLoginMissingUsername() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("admin123");

        mockMvc.perform(post("/api/v1/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Self-registration is disabled unless explicitly enabled")
    void registrationIsDisabledByDefault() throws Exception {
        when(authProperties.getRegistration()).thenReturn(new AuthProperties.Registration());

        mockMvc.perform(
                        post("/api/v1/token/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"new@example.com","password":"a-strong-password"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login is blocked when the account email is not verified")
    void loginBlockedWhenEmailNotVerified() throws Exception {
        AuthProperties.Registration registration = new AuthProperties.Registration();
        registration.setRequireEmailVerification(true);
        when(authProperties.getRegistration()).thenReturn(registration);
        AuthUser user =
                new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default", "admin@example.com", false);
        when(userDetailsService.loadByUsername("admin")).thenReturn(user);
        when(userDetailsService.validatePassword(any(), any())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        mockMvc.perform(post("/api/v1/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Self-registration issues a regular-user token when verification is not required")
    void registrationCreatesRegularUser() throws Exception {
        AuthProperties.Registration registration = new AuthProperties.Registration();
        registration.setEnabled(true);
        registration.setRequireEmailVerification(false);
        when(authProperties.getRegistration()).thenReturn(registration);
        AuthUser user = new AuthUser("new-id", "new@example.com", "hash", List.of("ROLE_USER"), "default");
        when(userDetailsService.register("new@example.com", "a-strong-password", true, "default"))
                .thenReturn(user);
        when(tokenService.issueTokenPair(user)).thenReturn(new TokenResponse("access", "refresh", 1800L));

        mockMvc.perform(
                        post("/api/v1/token/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"new@example.com","password":"a-strong-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access"));
    }

    @Test
    @DisplayName("Registration sends a verification mail instead of issuing tokens when verification is required")
    void registrationSendsVerificationMailWhenRequired() throws Exception {
        AuthProperties.Registration registration = new AuthProperties.Registration();
        registration.setEnabled(true);
        registration.setRequireEmailVerification(true);
        registration.setDefaultTenant("tenant-a");
        when(authProperties.getRegistration()).thenReturn(registration);
        AuthUser user = new AuthUser(
                "new-id", "new@example.com", "hash", List.of("ROLE_USER"), "tenant-a", "new@example.com", false);
        when(userDetailsService.register("new@example.com", "a-strong-password", false, "tenant-a"))
                .thenReturn(user);

        mockMvc.perform(
                        post("/api/v1/token/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"new@example.com","password":"a-strong-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        verify(emailVerificationService).sendVerificationEmail(user);
        verify(tokenService, never()).issueTokenPair(any());
    }

    @Test
    @DisplayName("Email verify endpoint returns success and rejects unknown tokens with 400")
    void emailVerifyEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/token/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"token":"abc"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        doThrow(new BizException(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .when(emailVerificationService)
                .verifyEmail("bad");
        mockMvc.perform(post("/api/v1/token/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"token":"bad"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resend and forgot-password always answer 200 to avoid account enumeration")
    void resendAndForgotAreEnumSafe() throws Exception {
        mockMvc.perform(post("/api/v1/token/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"email":"nobody@example.com"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/token/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"email":"nobody@example.com"}
                        """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Password reset endpoint returns success and validates the request body")
    void passwordResetEndpoint() throws Exception {
        mockMvc.perform(
                        post("/api/v1/token/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"token":"abc","newPassword":"a-fresh-password"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/v1/token/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"token":"abc"}
                        """))
                .andExpect(status().isBadRequest());
    }
}
