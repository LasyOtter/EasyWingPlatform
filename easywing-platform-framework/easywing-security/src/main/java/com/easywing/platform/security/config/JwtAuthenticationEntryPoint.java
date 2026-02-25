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
package com.easywing.platform.security.config;

import com.easywing.platform.core.constant.HttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT认证入口点
 * <p>
 * 处理认证失败，返回RFC 9457 Problem Detail格式的错误响应
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://api.easywing.io/errors/unauthorized");
        problem.put("title", "Unauthorized");
        problem.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        problem.put("detail", "未授权访问，请提供有效的认证令牌");
        problem.put("errorCode", "AUTH001");
        problem.put("timestamp", Instant.now().toString());
        problem.put("instance", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
