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

import com.easywing.platform.core.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局响应式异常处理器
 * <p>
 * Spring Cloud Gateway 基于 WebFlux，异常不会进入 MVC 的
 * {@code @RestControllerAdvice}。默认情况下未捕获的异常由
 * {@code DefaultErrorWebExceptionHandler} 处理，输出格式为
 * {@code {"timestamp":...,"path":...,"error":...}}，不符合平台 RFC 9457 规范。
 * <p>
 * 本处理器优先级高于 {@code ErrorWebFluxAutoConfiguration} 中的默认实现
 * （{@code @Order(-1)}），将所有未被过滤器链处理的异常统一转换为
 * RFC 9457 Problem Details，保证网关侧与后端服务输出一致。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
@Order(-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ErrorAttributes errorAttributes;

    public GatewayErrorWebExceptionHandler(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Unhandled gateway error: {}", ex.getMessage(), ex);

        HttpStatus status = resolveStatus(ex);
        return ProblemDetailSupport.write(
                exchange,
                status,
                status.getReasonPhrase(),
                "网关请求处理失败，请稍后重试",
                ErrorCode.SYSTEM_ERROR,
                exchange.getResponse());
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        // 网关自身不依赖 spring-security，此处按异常类名兜底识别，
        // 避免为了两个 instanceof 引入 security 依赖
        String exType = ex.getClass().getName();
        if (exType.equals("org.springframework.security.access.AccessDeniedException")) {
            return HttpStatus.FORBIDDEN;
        }
        if (exType.equals("org.springframework.security.core.AuthenticationException")
                || exType.endsWith("BadCredentialsException")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
