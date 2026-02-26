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
package com.easywing.platform.auth.service;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.TokenResponse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 令牌服务单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenService tokenService;
    private UserDetailsService userDetailsService;
    private AuthProperties properties;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();

        properties = new AuthProperties();
        properties.getJwt().setIssuer("https://test.easywing.com");
        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(30));
        properties.getJwt().setRefreshTokenTtl(Duration.ofDays(7));
        properties.getJwt().setKeyId("test-key");

        userDetailsService = new UserDetailsService(new BCryptPasswordEncoder());

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        tokenService = new TokenService(properties, rsaKey, redisTemplate, userDetailsService);
    }

    @Test
    @DisplayName("Issue token pair - should return access and refresh tokens")
    void testIssueTokenPair() {
        AuthUser user = new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default");

        TokenResponse response = tokenService.issueTokenPair(user);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(Duration.ofMinutes(30).getSeconds(), response.getExpiresIn());
    }

    @Test
    @DisplayName("Revoke token - should add token to blacklist")
    void testRevokeToken() {
        AuthUser user = new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default");
        TokenResponse tokens = tokenService.issueTokenPair(user);

        tokenService.revokeToken(tokens.getAccessToken());

        verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("Refresh token pair - should issue new tokens")
    void testRefreshTokenPair() {
        AuthUser user = new AuthUser("1", "admin", "hash", List.of("ROLE_ADMIN"), "default");
        TokenResponse initial = tokenService.issueTokenPair(user);

        TokenResponse refreshed = tokenService.refreshTokenPair(initial.getRefreshToken());

        assertNotNull(refreshed.getAccessToken());
        assertNotNull(refreshed.getRefreshToken());
        assertNotEquals(initial.getAccessToken(), refreshed.getAccessToken());
    }

    @Test
    @DisplayName("UserDetailsService - load existing user")
    void testLoadByUsername() {
        AuthUser user = userDetailsService.loadByUsername("admin");

        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertTrue(user.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("UserDetailsService - validate correct password")
    void testValidatePassword() {
        AuthUser user = userDetailsService.loadByUsername("admin");

        assertTrue(userDetailsService.validatePassword(user, "admin123"));
        assertFalse(userDetailsService.validatePassword(user, "wrongpassword"));
    }

    @Test
    @DisplayName("UserDetailsService - unknown user throws exception")
    void testLoadUnknownUser() {
        assertThrows(IllegalArgumentException.class, () -> userDetailsService.loadByUsername("unknown"));
    }
}
