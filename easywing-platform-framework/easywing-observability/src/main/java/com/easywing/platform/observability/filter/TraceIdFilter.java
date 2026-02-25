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
package com.easywing.platform.observability.filter;

import com.easywing.platform.core.constant.HttpHeaders;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId过滤器
 * <p>
 * 将OpenTelemetry的TraceId和SpanId注入到MDC中，便于日志输出
 * <p>
 * 同时将TraceId添加到响应头中，方便客户端追踪
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String SPAN_ID_MDC_KEY = "spanId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Context context = Context.current();
        Span currentSpan = Span.fromContext(context);

        String traceId = currentSpan.getSpanContext().getTraceId();
        String spanId = currentSpan.getSpanContext().getSpanId();

        // 检查请求头中是否有传入的TraceId
        String incomingTraceId = request.getHeader(HttpHeaders.X_TRACE_ID);
        if (incomingTraceId != null && !incomingTraceId.isEmpty()) {
            traceId = incomingTraceId;
        }

        try (Scope scope = context.makeCurrent()) {
            // 将TraceId和SpanId放入MDC
            if (traceId != null && !traceId.isEmpty()) {
                MDC.put(TRACE_ID_MDC_KEY, traceId);
            }
            if (spanId != null && !spanId.isEmpty()) {
                MDC.put(SPAN_ID_MDC_KEY, spanId);
            }

            // 将TraceId添加到响应头
            if (traceId != null && !traceId.isEmpty()) {
                response.setHeader(HttpHeaders.X_TRACE_ID, traceId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 清理MDC
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(SPAN_ID_MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 排除静态资源和健康检查
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/prometheus") ||
               path.startsWith("/static/") ||
               path.startsWith("/webjars/");
    }
}
