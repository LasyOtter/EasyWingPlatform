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
package com.easywing.platform.gateway.filter;

import com.easywing.platform.core.constant.HttpHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局请求ID过滤器
 * <p>
 * 为每个请求生成唯一的请求ID，并传递到下游服务
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        String traceId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = requestId;
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(builder -> builder
                        .header(HttpHeaders.X_REQUEST_ID, requestId)
                        .header(HttpHeaders.X_TRACE_ID, traceId))
                .response(builder -> builder.header(HttpHeaders.X_REQUEST_ID, requestId)
                        .header(HttpHeaders.X_TRACE_ID, traceId))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
