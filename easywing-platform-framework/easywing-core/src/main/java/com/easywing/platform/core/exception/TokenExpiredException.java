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
import java.util.Date;

/**
 * Token过期异常
 * <p>
 * 用于表示JWT Token已过期的情况
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class TokenExpiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Token主题（用户标识）
     */
    private final String subject;

    /**
     * 过期时间
     */
    private final Date expiredAt;

    /**
     * 构造Token过期异常
     *
     * @param subject  Token主题（用户标识）
     * @param expiredAt 过期时间
     */
    public TokenExpiredException(String subject, Date expiredAt) {
        super("Token expired for subject: " + subject);
        this.subject = subject;
        this.expiredAt = expiredAt;
    }

    /**
     * 构造Token过期异常
     *
     * @param subject   Token主题（用户标识）
     * @param expiredAt 过期时间
     * @param message   错误消息
     */
    public TokenExpiredException(String subject, Date expiredAt, String message) {
        super(message);
        this.subject = subject;
        this.expiredAt = expiredAt;
    }

    public String getSubject() {
        return subject;
    }

    public Date getExpiredAt() {
        return expiredAt;
    }
}
