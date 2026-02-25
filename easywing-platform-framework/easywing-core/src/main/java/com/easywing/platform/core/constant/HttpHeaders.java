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
package com.easywing.platform.core.constant;

/**
 * HTTP头常量
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class HttpHeaders {

    private HttpHeaders() {
    }

    // ==================== 标准HTTP头 ====================
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String USER_AGENT = "User-Agent";
    public static final String LOCATION = "Location";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    // ==================== 自定义HTTP头 ====================
    public static final String X_REQUEST_ID = "X-Request-Id";
    public static final String X_TRACE_ID = "X-Trace-Id";
    public static final String X_SPAN_ID = "X-Span-Id";
    public static final String X_TENANT_ID = "X-Tenant-Id";
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_REAL_IP = "X-Real-IP";

    // ==================== 灰度发布相关 ====================
    public static final String X_GRAY_VERSION = "X-Gray-Version";
    public static final String X_GRAY_WEIGHT = "X-Gray-Weight";
    public static final String X_CANARY = "X-Canary";

    // ==================== 认证相关 ====================
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BASIC_PREFIX = "Basic ";

    // ==================== Content-Type ====================
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_HTML = "text/html";
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String APPLICATION_GRPC = "application/grpc";

    // ==================== Trace传播头 ====================
    public static final String TRACE_PARENT = "traceparent";
    public static final String TRACE_STATE = "tracestate";
}
