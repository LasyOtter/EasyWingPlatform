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
 * Token签名验证失败异常
 * <p>
 * 用于表示JWT Token签名验证失败的情况
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class TokenSignatureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Token ID（可选）
     */
    private final String tokenId;

    /**
     * 构造Token签名异常
     *
     * @param message 错误消息
     */
    public TokenSignatureException(String message) {
        super(message);
        this.tokenId = null;
    }

    /**
     * 构造Token签名异常
     *
     * @param message 错误消息
     * @param cause   原因异常
     */
    public TokenSignatureException(String message, Throwable cause) {
        super(message, cause);
        this.tokenId = null;
    }

    /**
     * 构造Token签名异常
     *
     * @param message 错误消息
     * @param tokenId Token ID
     */
    public TokenSignatureException(String message, String tokenId) {
        super(message);
        this.tokenId = tokenId;
    }

    public String getTokenId() {
        return tokenId;
    }
}
