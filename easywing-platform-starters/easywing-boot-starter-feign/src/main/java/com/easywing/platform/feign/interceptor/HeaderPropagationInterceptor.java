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
package com.easywing.platform.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;

/**
 * Feign 请求头传递拦截器
 * <p>
 * 将上游请求的头信息传递到下游服务调用
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class HeaderPropagationInterceptor implements RequestInterceptor {

    private static final String[] DEFAULT_HEADERS = {
        "X-Request-Id", "X-Trace-Id", "X-Span-Id",
        "Authorization", "X-Tenant-Id", "X-User-Id"
    };

    private final String[] headerNames;

    public HeaderPropagationInterceptor(String[] headerNames) {
        this.headerNames = headerNames != null ? headerNames : DEFAULT_HEADERS;
    }

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return;
        }

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                template.header(headerName, headerValue);
                log.debug("Propagating header: {} = {}", headerName, maskSensitiveHeader(headerName, headerValue));
            }
        }
    }

    private String maskSensitiveHeader(String headerName, String value) {
        if ("Authorization".equalsIgnoreCase(headerName) && value != null && value.length() > 20) {
            return value.substring(0, 10) + "****" + value.substring(value.length() - 6);
        }
        return value;
    }
}
