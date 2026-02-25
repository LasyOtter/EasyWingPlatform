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
 * 系统异常
 * <p>
 * 用于表示系统级别的不可预期异常，如：
 * <ul>
 *     <li>数据库连接失败</li>
 *     <li>外部服务调用失败</li>
 *     <li>文件IO异常</li>
 *     <li>网络超时</li>
 * </ul>
 * <p>
 * 此类异常会被全局异常处理器捕获并转换为RFC 9457 Problem Detail格式响应
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class SystemException extends RuntimeException {

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

    public SystemException(String message) {
        this(ErrorCode.SYSTEM_ERROR, message);
    }

    public SystemException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = ErrorCode.INTERNAL_SERVER_ERROR_STATUS;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.SYSTEM_ERROR;
        this.status = ErrorCode.INTERNAL_SERVER_ERROR_STATUS;
    }

    public SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = ErrorCode.INTERNAL_SERVER_ERROR_STATUS;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}
