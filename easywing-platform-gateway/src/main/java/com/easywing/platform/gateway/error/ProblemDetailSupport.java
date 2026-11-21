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
package com.easywing.platform.gateway.error;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.ErrorCode;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 RFC 9457 错误响应工具
 * <p>
 * 网关基于 Spring Cloud Gateway（WebFlux），不能使用 Spring MVC 的
 * {@code ProblemDetail} 与 {@code @RestControllerAdvice}，因此直接构造与平台
 * 其他服务一致的 JSON 结构：
 * <ul>
 *     <li>标准字段：type / title / status / detail / instance</li>
 *     <li>扩展字段：errorCode / timestamp / traceId / errors</li>
 * </ul>
 * type URI 前缀与 {@code easywing-web} 的 {@code Rfc9457ProblemDetail} 保持一致
 * （{@value #TYPE_BASE_URI}），保证网关与后端服务输出结构相同。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class ProblemDetailSupport {

    /**
     * 与 easywing-web Rfc9457ProblemDetail.TYPE_BASE_URI 保持一致
     */
    private static final String TYPE_BASE_URI = "https://api.easywing.io/errors/";

    private ProblemDetailSupport() {}

    /**
     * 构建 type URI，规则与 easywing-web 一致
     */
    public static URI buildType(String errorCode) {
        return URI.create(TYPE_BASE_URI + (errorCode == null ? "" : errorCode));
    }

    /**
     * 写出 RFC 9457 错误响应
     *
     * @param exchange   当前请求
     * @param status     HTTP 状态码
     * @param title       问题标题
     * @param detail      问题详情
     * @param errorCode   应用错误码
     * @param response    响应对象
     * @return 写入完成的 {@link Mono}
     */
    public static Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String title,
            String detail,
            String errorCode,
            ServerHttpResponse response) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", buildType(errorCode).toString());
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getPath().value());
        body.put("errorCode", errorCode);
        body.put("timestamp", Instant.now().toString());
        String traceId = resolveTraceId(exchange);
        if (traceId != null && !traceId.isEmpty()) {
            body.put("traceId", traceId);
        }

        byte[] bytes = com.easywing.platform.gateway.util.JsonCodec.encode(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /**
     * 写出限流错误响应（429，带 Retry-After）
     */
    public static Mono<Void> writeTooManyRequests(
            ServerWebExchange exchange, String detail, ServerHttpResponse response) {
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate Limited", detail, ErrorCode.RATE_LIMITED, response);
    }

    /**
     * 写出未认证错误响应（401，带 WWW-Authenticate: Bearer）
     */
    public static Mono<Void> writeUnauthorized(
            ServerWebExchange exchange, String title, String detail, String errorCode, ServerHttpResponse response) {
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, title, detail, errorCode, response);
    }

    private static String resolveTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = exchange.getResponse().getHeaders().getFirst(HttpHeaders.X_TRACE_ID);
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_REQUEST_ID);
        }
        return traceId;
    }
}
