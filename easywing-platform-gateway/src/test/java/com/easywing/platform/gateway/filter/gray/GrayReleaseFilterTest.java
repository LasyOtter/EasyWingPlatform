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
package com.easywing.platform.gateway.filter.gray;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.GrayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 灰度发布过滤器测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class GrayReleaseFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private GrayReleaseFilter filter;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        GrayProperties grayProperties = properties.getGray();
        grayProperties.setEnabled(true);
        grayProperties.setDefaultVersion("v1");
        grayProperties.setHeaderName("X-Gray-Version");
        
        filter = new GrayReleaseFilter(properties);
    }

    @Test
    @DisplayName("Gray release disabled - should pass through")
    void testGrayReleaseDisabled() {
        properties.getGray().setEnabled(false);
        GrayReleaseFilter disabledFilter = new GrayReleaseFilter(properties);
        
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
    @DisplayName("Gray version from header - should use specified version")
    void testGrayVersionFromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header("X-Gray-Version", "v2")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        String grayVersion = exchange.getAttribute("grayVersion");
        assertEquals("v2", grayVersion);
    }

    @Test
    @DisplayName("No gray version specified - should use default")
    void testNoGrayVersionSpecified() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Weighted selector - should distribute versions based on weight")
    void testWeightedSelector() {
        GrayProperties.ServiceConfig serviceConfig = new GrayProperties.ServiceConfig();
        serviceConfig.setServiceId("user-service");
        serviceConfig.setDefaultVersion("v1");
        
        GrayProperties.VersionConfig v1 = new GrayProperties.VersionConfig();
        v1.setVersion("v1");
        v1.setWeight(90);
        
        GrayProperties.VersionConfig v2 = new GrayProperties.VersionConfig();
        v2.setVersion("v2");
        v2.setWeight(10);
        
        serviceConfig.setVersions(List.of(v1, v2));
        properties.getGray().setServices(List.of(serviceConfig));
        
        filter.refreshSelectors();
        
        int v1Count = 0;
        int v2Count = 0;
        
        for (int i = 0; i < 100; i++) {
            String userId = "user" + i;
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users")
                    .header(HttpHeaders.X_USER_ID, userId)
                    .build();
            
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            Route route = Route.async()
                    .id("user-service")
                    .uri(URI.create("lb://user-service"))
                    .predicate(ex -> true)
                    .build();
            
            exchange.getAttributes().put(
                    org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                    route
            );
            
            when(chain.filter(any())).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            String version = exchange.getAttribute("grayVersion");
            if ("v2".equals(version)) {
                v2Count++;
            } else {
                v1Count++;
            }
        }
        
        assertTrue(v1Count > v2Count, "v1 should receive more traffic than v2");
    }

    @Test
    @DisplayName("User ID rule - should route specific users to target version")
    void testUserIdRule() {
        GrayProperties.ServiceConfig serviceConfig = new GrayProperties.ServiceConfig();
        serviceConfig.setServiceId("user-service");
        serviceConfig.setDefaultVersion("v1");
        
        GrayProperties.VersionConfig v1 = new GrayProperties.VersionConfig();
        v1.setVersion("v1");
        v1.setWeight(100);
        serviceConfig.setVersions(List.of(v1));
        
        GrayProperties.RuleConfig rule = new GrayProperties.RuleConfig();
        rule.setType("user_id");
        rule.setMatchValue("beta-user");
        rule.setTargetVersion("v2");
        serviceConfig.setRules(List.of(rule));
        
        properties.getGray().setServices(List.of(serviceConfig));
        filter.refreshSelectors();
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.X_USER_ID, "beta-user")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        Route route = Route.async()
                .id("user-service")
                .uri(URI.create("lb://user-service"))
                .predicate(ex -> true)
                .build();
        
        exchange.getAttributes().put(
                org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                route
        );
        
        when(chain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        String grayVersion = exchange.getAttribute("grayVersion");
        assertEquals("v2", grayVersion);
    }
}
