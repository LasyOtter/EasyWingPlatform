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

import com.easywing.platform.auth.config.AuthSecurityConfig;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.LoginRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.auth.service.UserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 令牌控制器单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@WebMvcTest(TokenController.class)
@Import(AuthSecurityConfig.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserDetailsService userDetailsService;

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

        mockMvc.perform(post("/token/login")
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

        mockMvc.perform(post("/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with missing username - should return 400")
    void testLoginMissingUsername() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("admin123");

        mockMvc.perform(post("/token/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
