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
package com.easywing.platform.messaging.converter;

/**
 * 消息转换异常
 * <p>
 * 消息序列化和反序列化过程中的异常
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class ConversionException extends RuntimeException {

    /**
     * 构造转换异常
     *
     * @param message 错误消息
     */
    public ConversionException(String message) {
        super(message);
    }

    /**
     * 构造转换异常
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造转换异常
     *
     * @param cause 原始异常
     */
    public ConversionException(Throwable cause) {
        super(cause);
    }
}
