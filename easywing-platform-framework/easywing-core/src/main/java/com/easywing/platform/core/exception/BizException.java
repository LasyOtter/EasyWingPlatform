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

import java.io.Serial;

/**
 * 业务异常基类
 * <p>
 * 用于表示业务逻辑中的可预期异常，如：
 * <ul>
 *     <li>业务规则校验失败</li>
 *     <li>资源不存在</li>
 *     <li>状态不允许操作</li>
 *     <li>权限不足</li>
 * </ul>
 * <p>
 * 此类异常会被全局异常处理器捕获并转换为RFC 9457 Problem Detail格式响应
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * HTTP状态码
     */
    private final int status;

    /**
     * 错误标题
     */
    private final String title;

    /**
     * 错误详情
     */
    private final String detail;

    /**
     * 构造业务异常
     *
     * @param errorCode 错误码
     */
    public BizException(String errorCode) {
        this(errorCode, ErrorCode.getMessage(errorCode));
    }

    /**
     * 构造业务异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public BizException(String errorCode, String message) {
        this(errorCode, ErrorCode.getStatus(errorCode), ErrorCode.getTitle(errorCode), message);
    }

    /**
     * 构造业务异常
     *
     * @param errorCode 错误码
     * @param status    HTTP状态码
     * @param title     错误标题
     * @param detail    错误详情
     */
    public BizException(String errorCode, int status, String title, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /**
     * 构造业务异常
     *
     * @param errorCode 错误码
     * @param cause     原因异常
     */
    public BizException(String errorCode, Throwable cause) {
        this(errorCode, ErrorCode.getMessage(errorCode), cause);
    }

    /**
     * 构造业务异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因异常
     */
    public BizException(String errorCode, String message, Throwable cause) {
        this(errorCode, ErrorCode.getStatus(errorCode), ErrorCode.getTitle(errorCode), message);
        initCause(cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }
}
