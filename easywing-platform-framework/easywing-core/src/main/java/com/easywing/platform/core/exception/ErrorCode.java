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

    private ErrorCode() {}

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
    /** 一次性链接（邮箱验证 / 密码重置）无效或已过期 */
    public static final String INVALID_OR_EXPIRED_TOKEN = "AUTH006";

    // ==================== 安全错误 ====================
    public static final String FORBIDDEN = "SEC001";
    public static final String ACCESS_DENIED = "SEC002";
    public static final String INSUFFICIENT_SCOPE = "SEC003";
    public static final String WEAK_PASSWORD = "SEC004";
    public static final String PASSWORD_REUSED = "SEC005";
    public static final String ACCOUNT_LOCKED = "SEC006";
    /** 邮箱尚未通过验证 */
    public static final String EMAIL_NOT_VERIFIED = "SEC007";

    // ==================== 业务错误 ====================
    public static final String RESOURCE_NOT_FOUND = "BIZ001";
    public static final String RESOURCE_ALREADY_EXISTS = "BIZ002";
    public static final String RESOURCE_CONFLICT = "BIZ003";
    public static final String OPERATION_NOT_ALLOWED = "BIZ004";
    public static final String BUSINESS_RULE_VIOLATION = "BIZ005";
    public static final String USERNAME_EXISTS = "BIZ006";
    public static final String PAGE_TOO_DEEP = "BIZ007";
    public static final String EXPORT_SIZE_EXCEEDED = "BIZ008";
    public static final String BATCH_SIZE_EXCEEDED = "BIZ009";
    public static final String PROTECTED_USER_CANNOT_DELETE = "BIZ010";

    // ==================== 限流熔断错误 ====================
    public static final String RATE_LIMITED = "RATE001";
    public static final String RETRY_EXHAUSTED = "RATE002";

    // ==================== 幂等性错误 ====================
    public static final String REQUEST_DUPLICATE = "IDEM001";
    public static final String RATE_LIMIT_EXCEEDED = "RATE003";

    // ==================== AID 漫剧工坊错误 ====================
    /** 项目 / 分集 / 资产 / 分镜等资源不存在 */
    public static final String AID_RESOURCE_NOT_FOUND = "AID001";
    /** 资产编号在项目内重复 */
    public static final String AID_ASSET_CODE_EXISTS = "AID002";
    /** 生成任务不存在 */
    public static final String AID_TASK_NOT_FOUND = "AID003";
    /** 任务状态不允许该操作（如对已完成任务重新生成） */
    public static final String AID_TASK_STATE_INVALID = "AID004";
    /** 上游 Provider 调用失败 */
    public static final String AID_PROVIDER_ERROR = "AID005";
    /** Provider 参数超出模型能力（分辨率/时长/比例/参考图数量） */
    public static final String AID_CAPABILITY_EXCEEDED = "AID006";
    /** 引用的资产在当前项目内不存在 */
    public static final String AID_ASSET_REF_UNRESOLVED = "AID007";
    /** 提示词命中禁用词 */
    public static final String AID_PROMPT_BANNED_WORD = "AID008";
    /** 并发槽位已满，任务排队中 */
    public static final String AID_SLOT_FULL = "AID009";
    /** 余额不足，无法预冻结 */
    public static final String AID_BALANCE_INSUFFICIENT = "AID010";
    /** FFmpeg 合成失败 */
    public static final String AID_FFMPEG_FAILED = "AID011";
    /** 导出任务已过期或产物已被清理 */
    public static final String AID_EXPORT_EXPIRED = "AID012";

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
        register(INVALID_OR_EXPIRED_TOKEN, BAD_REQUEST_STATUS, "Invalid Or Expired Token", "链接无效或已过期");

        // 安全错误
        register(FORBIDDEN, FORBIDDEN_STATUS, "Forbidden", "禁止访问");
        register(ACCESS_DENIED, FORBIDDEN_STATUS, "Access Denied", "权限不足");
        register(INSUFFICIENT_SCOPE, FORBIDDEN_STATUS, "Insufficient Scope", "权限范围不足");
        register(WEAK_PASSWORD, BAD_REQUEST_STATUS, "Weak Password", "密码强度不足");
        register(PASSWORD_REUSED, BAD_REQUEST_STATUS, "Password Reused", "密码近期已使用");
        register(ACCOUNT_LOCKED, FORBIDDEN_STATUS, "Account Locked", "账户已锁定");
        register(EMAIL_NOT_VERIFIED, FORBIDDEN_STATUS, "Email Not Verified", "邮箱尚未验证");

        // 业务错误
        register(RESOURCE_NOT_FOUND, NOT_FOUND_STATUS, "Resource Not Found", "请求的资源不存在");
        register(RESOURCE_ALREADY_EXISTS, CONFLICT_STATUS, "Resource Already Exists", "资源已存在");
        register(RESOURCE_CONFLICT, CONFLICT_STATUS, "Resource Conflict", "资源冲突");
        register(OPERATION_NOT_ALLOWED, FORBIDDEN_STATUS, "Operation Not Allowed", "操作不被允许");
        register(BUSINESS_RULE_VIOLATION, UNPROCESSABLE_ENTITY_STATUS, "Business Rule Violation", "业务规则校验失败");
        register(USERNAME_EXISTS, CONFLICT_STATUS, "Username Exists", "用户名已存在");
        register(PAGE_TOO_DEEP, BAD_REQUEST_STATUS, "Page Too Deep", "页码过大，请添加筛选条件");
        register(EXPORT_SIZE_EXCEEDED, BAD_REQUEST_STATUS, "Export Size Exceeded", "导出数据量过大，请添加筛选条件");
        register(BATCH_SIZE_EXCEEDED, BAD_REQUEST_STATUS, "Batch Size Exceeded", "单次操作数量超过限制");
        register(PROTECTED_USER_CANNOT_DELETE, FORBIDDEN_STATUS, "Protected User Cannot Delete", "受保护用户不能被删除");

        // 限流熔断
        register(RATE_LIMITED, TOO_MANY_REQUESTS_STATUS, "Rate Limited", "请求过于频繁，请稍后重试");
        register(RETRY_EXHAUSTED, SERVICE_UNAVAILABLE_STATUS, "Retry Exhausted", "重试次数已用尽");
        register(RATE_LIMIT_EXCEEDED, TOO_MANY_REQUESTS_STATUS, "Rate Limit Exceeded", "请求过于频繁，请稍后再试");

        // 幂等性错误
        register(REQUEST_DUPLICATE, TOO_MANY_REQUESTS_STATUS, "Duplicate Request", "请求处理中，请勿重复提交");

        // AID 漫剧工坊
        register(AID_RESOURCE_NOT_FOUND, NOT_FOUND_STATUS, "AID Resource Not Found", "创作资源不存在");
        register(AID_ASSET_CODE_EXISTS, CONFLICT_STATUS, "AID Asset Code Exists", "资产编号在项目内已存在");
        register(AID_TASK_NOT_FOUND, NOT_FOUND_STATUS, "AID Task Not Found", "生成任务不存在");
        register(AID_TASK_STATE_INVALID, UNPROCESSABLE_ENTITY_STATUS, "AID Task State Invalid", "当前任务状态不允许该操作");
        register(AID_PROVIDER_ERROR, SERVICE_UNAVAILABLE_STATUS, "AID Provider Error", "上游模型服务调用失败");
        register(AID_CAPABILITY_EXCEEDED, BAD_REQUEST_STATUS, "AID Capability Exceeded", "参数超出目标模型的能力范围");
        register(AID_ASSET_REF_UNRESOLVED, BAD_REQUEST_STATUS, "AID Asset Reference Unresolved", "提示词引用了不存在的资产");
        register(AID_PROMPT_BANNED_WORD, UNPROCESSABLE_ENTITY_STATUS, "AID Prompt Banned Word", "提示词命中禁用词");
        register(AID_SLOT_FULL, TOO_MANY_REQUESTS_STATUS, "AID Slot Full", "并发槽位已满，任务已排队");
        register(AID_BALANCE_INSUFFICIENT, UNPROCESSABLE_ENTITY_STATUS, "AID Balance Insufficient", "账户余额不足");
        register(AID_FFMPEG_FAILED, INTERNAL_SERVER_ERROR_STATUS, "AID FFmpeg Failed", "视频合成失败");
        register(AID_EXPORT_EXPIRED, NOT_FOUND_STATUS, "AID Export Expired", "导出产物已过期或已被清理");
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

    private record ErrorInfo(String code, int status, String title, String message) {}
}
