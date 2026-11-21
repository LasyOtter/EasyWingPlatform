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
package com.easywing.platform.led.sdk;

/**
 * LED SDK 调用异常，携带错误码与官方描述。
 * <p>
 * 统一转换为 EasyWing 的 {@link com.easywing.platform.core.exception.BizException} 对外暴露，
 * 此类用于 SDK 适配层内部传递错误码语义。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class LedException extends RuntimeException {

    private final int errCode;
    private final String action;

    public LedException(String action, int errCode, String detail) {
        this(action, errCode, detail, null);
    }

    public LedException(String action, int errCode, String detail, Throwable cause) {
        super(
                "[" + action + "] 失败, errCode=" + errCode
                        + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"),
                cause);
        this.action = action;
        this.errCode = errCode;
    }

    public int errCode() {
        return errCode;
    }

    public String action() {
        return action;
    }
}
