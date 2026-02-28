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
package com.easywing.platform.core.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误码常量
 * <p>
 * 定义系统标准的错误码、HTTP状态码、标题和消息
 * <p>
 * 错误码规范：
 * <ul>
 *     <li>格式：{模块}{类型}{序号}，如 BIZ001</li>
 *     <li>SYS: 系统级错误</li>
 *     <li>BIZ: 业务级错误</li>
 *     <li>VAL: 验证错误</li>
 *     <li>AUTH: 认证错误</li>
 *     <li>SEC: 安全错误</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // ==================== HTTP状态码 ====================
    public static final int BAD_REQUEST_STATUS = 400;
    public static final int UNAUTHORIZED_STATUS = 401;
    public static final int FORBIDDEN_STATUS = 403;
    public static final int NOT_FOUND_STATUS = 404;
    public static final int METHOD_NOT_ALLOWED_STATUS = 405;
    public static final int CONFLICT_STATUS = 409;
    public static final int UNPROCESSABLE_ENTITY_STATUS = 422;
    public static final int TOO_MANY_REQUESTS_STATUS = 429;
    public static final int INTERNAL_SERVER_ERROR_STATUS = 500;
    public static final int SERVICE_UNAVAILABLE_STATUS = 503;

    // ==================== 系统错误 ====================
    public static final String SYSTEM_ERROR = "SYS001";
    public static final String SERVICE_UNAVAILABLE = "SYS002";
    public static final String GATEWAY_TIMEOUT = "SYS003";
    public static final String CIRCUIT_BREAKER_OPEN = "SYS004";

    // ==================== 验证错误 ====================
    public static final String VALIDATION_ERROR = "VAL001";
    public static final String INVALID_PARAMETER = "VAL002";
    public static final String MISSING_PARAMETER = "VAL003";
    public static final String INVALID_FORMAT = "VAL004";

    // ==================== 认证错误 ====================
    public static final String UNAUTHORIZED = "AUTH001";
    public static final String TOKEN_EXPIRED = "AUTH002";
    public static final String TOKEN_INVALID = "AUTH003";
    public static final String TOKEN_MISSING = "AUTH004";
    public static final String INVALID_CREDENTIALS = "AUTH005";

    // ==================== 安全错误 ====================
    public static final String FORBIDDEN = "SEC001";
    public static final String ACCESS_DENIED = "SEC002";
    public static final String INSUFFICIENT_SCOPE = "SEC003";
    public static final String WEAK_PASSWORD = "SEC004";
    public static final String PASSWORD_REUSED = "SEC005";
    public static final String ACCOUNT_LOCKED = "SEC006";

    // ==================== 业务错误 ====================
    public static final String RESOURCE_NOT_FOUND = "BIZ001";
    public static final String RESOURCE_ALREADY_EXISTS = "BIZ002";
    public static final String RESOURCE_CONFLICT = "BIZ003";
    public static final String OPERATION_NOT_ALLOWED = "BIZ004";
    public static final String BUSINESS_RULE_VIOLATION = "BIZ005";
    public static final String USERNAME_EXISTS = "BIZ006";
    public static final String PAGE_TOO_DEEP = "BIZ007";
    public static final String EXPORT_SIZE_EXCEEDED = "BIZ008";

    // ==================== 限流熔断错误 ====================
    public static final String RATE_LIMITED = "RATE001";
    public static final String RETRY_EXHAUSTED = "RATE002";

    // ==================== 幂等性错误 ====================
    public static final String REQUEST_DUPLICATE = "IDEM001";
    public static final String RATE_LIMIT_EXCEEDED = "RATE003";

    // ==================== 错误信息映射 ====================
    private static final Map<String, ErrorInfo> ERROR_MAP = new HashMap<>();

    static {
        // 系统错误
        register(SYSTEM_ERROR, INTERNAL_SERVER_ERROR_STATUS, "System Error", "系统内部错误，请稍后重试");
        register(SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_STATUS, "Service Unavailable", "服务暂时不可用");
        register(GATEWAY_TIMEOUT, 504, "Gateway Timeout", "网关请求超时");
        register(CIRCUIT_BREAKER_OPEN, SERVICE_UNAVAILABLE_STATUS, "Circuit Breaker Open", "熔断器已开启，服务暂时不可用");

        // 验证错误
        register(VALIDATION_ERROR, BAD_REQUEST_STATUS, "Validation Error", "请求参数验证失败");
        register(INVALID_PARAMETER, BAD_REQUEST_STATUS, "Invalid Parameter", "无效的请求参数");
        register(MISSING_PARAMETER, BAD_REQUEST_STATUS, "Missing Parameter", "缺少必要参数");
        register(INVALID_FORMAT, BAD_REQUEST_STATUS, "Invalid Format", "参数格式不正确");

        // 认证错误
        register(UNAUTHORIZED, UNAUTHORIZED_STATUS, "Unauthorized", "未授权访问");
        register(TOKEN_EXPIRED, UNAUTHORIZED_STATUS, "Token Expired", "令牌已过期");
        register(TOKEN_INVALID, UNAUTHORIZED_STATUS, "Token Invalid", "无效的令牌");
        register(TOKEN_MISSING, UNAUTHORIZED_STATUS, "Token Missing", "缺少认证令牌");
        register(INVALID_CREDENTIALS, UNAUTHORIZED_STATUS, "Invalid Credentials", "用户名或密码错误");

        // 安全错误
        register(FORBIDDEN, FORBIDDEN_STATUS, "Forbidden", "禁止访问");
        register(ACCESS_DENIED, FORBIDDEN_STATUS, "Access Denied", "权限不足");
        register(INSUFFICIENT_SCOPE, FORBIDDEN_STATUS, "Insufficient Scope", "权限范围不足");
        register(WEAK_PASSWORD, BAD_REQUEST_STATUS, "Weak Password", "密码强度不足");
        register(PASSWORD_REUSED, BAD_REQUEST_STATUS, "Password Reused", "密码近期已使用");
        register(ACCOUNT_LOCKED, FORBIDDEN_STATUS, "Account Locked", "账户已锁定");

        // 业务错误
        register(RESOURCE_NOT_FOUND, NOT_FOUND_STATUS, "Resource Not Found", "请求的资源不存在");
        register(RESOURCE_ALREADY_EXISTS, CONFLICT_STATUS, "Resource Already Exists", "资源已存在");
        register(RESOURCE_CONFLICT, CONFLICT_STATUS, "Resource Conflict", "资源冲突");
        register(OPERATION_NOT_ALLOWED, FORBIDDEN_STATUS, "Operation Not Allowed", "操作不被允许");
        register(BUSINESS_RULE_VIOLATION, UNPROCESSABLE_ENTITY_STATUS, "Business Rule Violation", "业务规则校验失败");
        register(USERNAME_EXISTS, CONFLICT_STATUS, "Username Exists", "用户名已存在");
        register(PAGE_TOO_DEEP, BAD_REQUEST_STATUS, "Page Too Deep", "页码过大，请添加筛选条件");
        register(EXPORT_SIZE_EXCEEDED, BAD_REQUEST_STATUS, "Export Size Exceeded", "导出数据量过大，请添加筛选条件");

        // 限流熔断
        register(RATE_LIMITED, TOO_MANY_REQUESTS_STATUS, "Rate Limited", "请求过于频繁，请稍后重试");
        register(RETRY_EXHAUSTED, SERVICE_UNAVAILABLE_STATUS, "Retry Exhausted", "重试次数已用尽");
        register(RATE_LIMIT_EXCEEDED, TOO_MANY_REQUESTS_STATUS, "Rate Limit Exceeded", "请求过于频繁，请稍后再试");

        // 幂等性错误
        register(REQUEST_DUPLICATE, TOO_MANY_REQUESTS_STATUS, "Duplicate Request", "请求处理中，请勿重复提交");
    }

    private static void register(String code, int status, String title, String message) {
        ERROR_MAP.put(code, new ErrorInfo(code, status, title, message));
    }

    public static int getStatus(String code) {
        ErrorInfo info = ERROR_MAP.get(code);
        return info != null ? info.status() : INTERNAL_SERVER_ERROR_STATUS;
    }

    public static String getTitle(String code) {
        ErrorInfo info = ERROR_MAP.get(code);
        return info != null ? info.title() : "Error";
    }

    public static String getMessage(String code) {
        ErrorInfo info = ERROR_MAP.get(code);
        return info != null ? info.message() : "未知错误";
    }

    public static ErrorInfo getErrorInfo(String code) {
        return ERROR_MAP.get(code);
    }

    private record ErrorInfo(String code, int status, String title, String message) {
    }
}
