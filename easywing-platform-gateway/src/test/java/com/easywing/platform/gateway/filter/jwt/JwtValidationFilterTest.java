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
package com.easywing.platform.gateway.filter.jwt;

import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JWT校验过滤器测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private JwtValidationFilter filter;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        JwtProperties jwtProperties = properties.getJwt();
        jwtProperties.setEnabled(true);
        jwtProperties.setIgnorePaths(List.of("/actuator/**", "/api/auth/**"));
        jwtProperties.setCacheTtl(Duration.ofMinutes(5));
        jwtProperties.setCacheMaxSize(10000);
        
        filter = new JwtValidationFilter(properties);
    }

    @Test
    @DisplayName("JWT disabled - should pass through")
    void testJwtDisabled() {
        properties.getJwt().setEnabled(false);
        JwtValidationFilter disabledFilter = new JwtValidationFilter(properties);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(disabledFilter.filter(exchange, chain))
                .verifyComplete();
        
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Ignored path - should pass through without JWT")
    void testIgnoredPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Missing Authorization header - should return 401")
    void testMissingAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Invalid Authorization header format - should return 401")
    void testInvalidAuthorizationHeaderFormat() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "InvalidToken")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("JwtClaims - should correctly store claims")
    void testJwtClaims() {
        JwtClaims claims = new JwtClaims(
                "user123",
                "testuser",
                "https://auth.easywing.com",
                List.of("ROLE_USER", "ROLE_ADMIN"),
                "tenant1",
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600),
                java.util.Map.of("custom", "value")
        );
        
        assertEquals("user123", claims.getSubject());
        assertEquals("testuser", claims.getUsername());
        assertEquals("https://auth.easywing.com", claims.getIssuer());
        assertEquals(2, claims.getRoles().size());
        assertEquals("tenant1", claims.getTenantId());
        assertFalse(claims.isExpired());
        assertEquals("value", claims.getClaim("custom"));
    }

    @Test
    @DisplayName("JwtClaims - expired token should be detected")
    void testExpiredJwtClaims() {
        JwtClaims claims = new JwtClaims(
                "user123",
                "testuser",
                "https://auth.easywing.com",
                List.of("ROLE_USER"),
                null,
                java.time.Instant.now().minusSeconds(7200),
                java.time.Instant.now().minusSeconds(3600),
                null
        );
        
        assertTrue(claims.isExpired());
    }
}
