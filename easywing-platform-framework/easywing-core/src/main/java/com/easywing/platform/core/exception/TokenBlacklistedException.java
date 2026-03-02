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

/**
 * Token已注销异常
 * <p>
 * 用于表示JWT Token已被加入黑名单（已注销/已强制下线）的情况
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class TokenBlacklistedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * JWT ID
     */
    private final String jti;

    /**
     * 注销时间
     */
    private final long revokedAt;

    /**
     * 构造Token已注销异常
     *
     * @param jti JWT ID
     */
    public TokenBlacklistedException(String jti) {
        super("Token has been revoked, jti: " + jti);
        this.jti = jti;
        this.revokedAt = System.currentTimeMillis();
    }

    /**
     * 构造Token已注销异常
     *
     * @param jti     JWT ID
     * @param message 错误消息
     */
    public TokenBlacklistedException(String jti, String message) {
        super(message);
        this.jti = jti;
        this.revokedAt = System.currentTimeMillis();
    }

    public String getJti() {
        return jti;
    }

    public long getRevokedAt() {
        return revokedAt;
    }
}
