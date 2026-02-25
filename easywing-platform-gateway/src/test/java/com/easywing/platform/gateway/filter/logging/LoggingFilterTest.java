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

import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.LoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 日志过滤器测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoggingFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private LoggingFilter filter;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        LoggingProperties loggingProperties = properties.getLogging();
        loggingProperties.setEnabled(true);
        loggingProperties.setDesensitize(true);
        loggingProperties.setSampleRate(1.0);
        
        filter = new LoggingFilter(properties);
    }

    @Test
    @DisplayName("Logging disabled - should pass through")
    void testLoggingDisabled() {
        properties.getLogging().setEnabled(false);
        LoggingFilter disabledFilter = new LoggingFilter(properties);
        
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
    @DisplayName("Access log created - should log request")
    void testAccessLogCreated() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header("X-Request-Id", "test-request-id")
                .header("X-Trace-Id", "test-trace-id")
                .header("X-User-Id", "user123")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        AccessLog accessLog = exchange.getAttribute("accessLog");
        assertNotNull(accessLog);
        assertEquals("test-request-id", accessLog.getRequestId());
        assertEquals("test-trace-id", accessLog.getTraceId());
        assertEquals("user123", accessLog.getUserId());
        assertEquals("GET", accessLog.getMethod());
        assertEquals("/api/users", accessLog.getPath());
    }

    @Test
    @DisplayName("Access log - should capture response status")
    void testAccessLogResponseStatus() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        AccessLog accessLog = exchange.getAttribute("accessLog");
        assertNotNull(accessLog);
        assertEquals(HttpStatus.OK.value(), accessLog.getStatus());
    }

    @Test
    @DisplayName("Access log - should capture error status")
    void testAccessLogErrorStatus() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        AccessLog accessLog = exchange.getAttribute("accessLog");
        assertNotNull(accessLog);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), accessLog.getStatus());
    }

    @Test
    @DisplayName("Access log - should calculate duration")
    void testAccessLogDuration() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        AccessLog accessLog = exchange.getAttribute("accessLog");
        assertNotNull(accessLog);
        assertTrue(accessLog.getDuration() >= 0);
    }
}
