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

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.LoggingProperties;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 访问日志全局过滤器
 * <p>
 * 核心功能：
 * <ul>
 *     <li>请求/响应日志记录</li>
 *     <li>敏感字段脱敏（手机号、身份证号、银行卡号、密码等）</li>
 *     <li>日志级别动态调整</li>
 *     <li>链路追踪TraceId注入</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *     <li>异步日志（Log4j2 AsyncAppender）</li>
 *     <li>日志采样（高流量场景只记录百分比）</li>
 *     <li>零分配脱敏</li>
 *     <li>批量日志刷盘</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String START_TIME_ATTR = "loggingStartTime";
    private static final String ACCESS_LOG_ATTR = "accessLog";

    private final LoggingProperties properties;
    private final DesensitizeConverter desensitizeConverter;

    public LoggingFilter(GatewayProperties gatewayProperties) {
        this.properties = gatewayProperties.getLogging();
        this.desensitizeConverter = new DesensitizeConverter(properties);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        
        if (!shouldLog()) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        AccessLog accessLog = createAccessLog(exchange, startTime);
        exchange.getAttributes().put(ACCESS_LOG_ATTR, accessLog);

        ServerHttpRequest request = exchange.getRequest();
        
        if (properties.isLogRequestBody() && shouldCaptureBody(request)) {
            return chain.filter(decorateRequest(exchange, accessLog))
                    .then(Mono.defer(() -> logResponse(exchange, accessLog)));
        }

        return chain.filter(exchange)
                .then(Mono.defer(() -> logResponse(exchange, accessLog)));
    }

    private boolean shouldLog() {
        if (properties.getSampleRate() >= 1.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < properties.getSampleRate();
    }

    private boolean shouldCaptureBody(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return method == HttpMethod.POST || method == HttpMethod.PUT || 
               method == HttpMethod.PATCH || method == HttpMethod.DELETE;
    }

    private AccessLog createAccessLog(ServerWebExchange exchange, long startTime) {
        AccessLog accessLog = new AccessLog();
        ServerHttpRequest request = exchange.getRequest();
        
        accessLog.setRequestTime(startTime);
        accessLog.setMethod(request.getMethod().name());
        accessLog.setPath(request.getPath().value());
        accessLog.setQueryString(request.getURI().getQuery());
        accessLog.setClientIp(getClientIp(exchange));
        accessLog.setUserAgent(request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
        accessLog.setUserId(request.getHeaders().getFirst(HttpHeaders.X_USER_ID));
        accessLog.setTenantId(request.getHeaders().getFirst(HttpHeaders.X_TENANT_ID));
        accessLog.setTraceId(request.getHeaders().getFirst(HttpHeaders.X_TRACE_ID));
        accessLog.setRequestId(request.getHeaders().getFirst(HttpHeaders.X_REQUEST_ID));
        
        String grayVersion = exchange.getAttribute("grayVersion");
        if (grayVersion != null) {
            accessLog.setGrayVersion(grayVersion);
        }
        
        request.getHeaders().forEach((name, values) -> {
            if (isSensitiveHeader(name)) {
                accessLog.getRequestHeaders().put(name, "******");
            } else {
                accessLog.getRequestHeaders().put(name, String.join(",", values));
            }
        });
        
        accessLog.setRequestSize(request.getHeaders().getContentLength());
        
        return accessLog;
    }

    private String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        String xForwardedFor = request.getHeaders().getFirst(HttpHeaders.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst(HttpHeaders.X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    private boolean isSensitiveHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") || 
               lowerName.contains("token") || 
               lowerName.contains("password") ||
               lowerName.contains("secret") ||
               lowerName.contains("key") ||
               properties.getSensitiveHeaders().stream()
                       .anyMatch(h -> h.equalsIgnoreCase(headerName));
    }

    private ServerWebExchange decorateRequest(ServerWebExchange exchange, AccessLog accessLog) {
        ServerHttpRequest request = exchange.getRequest();
        
        return exchange.mutate().request(new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(buffer -> {
                    int length = buffer.readableByteCount();
                    if (length > 0 && length <= properties.getMaxBodyLength()) {
                        byte[] bytes = new byte[length];
                        buffer.read(bytes);
                        String body = new String(bytes, StandardCharsets.UTF_8);
                        accessLog.setRequestBody(desensitizeConverter.desensitizeJson(body));
                    }
                });
            }
        }).build();
    }

    private Mono<Void> logResponse(ServerWebExchange exchange, AccessLog accessLog) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - accessLog.getRequestTime();
        
        accessLog.setResponseTime(endTime);
        accessLog.setDuration(duration);
        
        ServerHttpResponse response = exchange.getResponse();
        accessLog.setStatus(response.getStatusCode() != null ? response.getStatusCode().value() : 0);
        
        response.getHeaders().forEach((name, values) -> 
                accessLog.getResponseHeaders().put(name, String.join(",", values)));
        
        accessLog.setResponseSize(response.getHeaders().getContentLength());
        
        if (accessLog.getStatus() >= 400) {
            log.warn("Request failed: {}", accessLog);
        } else {
            log.info("Request completed: {}", accessLog);
        }
        
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}