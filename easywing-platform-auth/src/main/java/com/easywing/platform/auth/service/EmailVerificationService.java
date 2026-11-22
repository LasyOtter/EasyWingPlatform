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
package com.easywing.platform.auth.service;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.service.mail.MailNotifier;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮箱验证与密码重置服务
 * <p>
 * 令牌存储在 Redis，键为 {@code auth:email-verify:<token>} / {@code auth:pwd-reset:<token>}，
 * 值为 userId，带 TTL 且单次有效（消费后立即删除）。重置密码成功后撤销该用户全部令牌，
 * 避免旧会话继续持有权限。
 * <p>
 * 防账号枚举：{@code resendVerificationEmail} 与 {@code forgotPassword} 对不存在的邮箱
 * 与已验证邮箱同样静默返回，不暴露账号是否存在。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class EmailVerificationService {

    private static final String VERIFY_PREFIX = "auth:email-verify:";
    private static final String RESET_PREFIX = "auth:pwd-reset:";
    private static final String VERIFY_PATH = "/verify-email";
    private static final String RESET_PATH = "/reset-password";

    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;
    private final TokenService tokenService;
    private final MailNotifier mailNotifier;
    private final AuthProperties properties;

    public EmailVerificationService(
            StringRedisTemplate redisTemplate,
            UserDetailsService userDetailsService,
            TokenService tokenService,
            MailNotifier mailNotifier,
            AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
        this.mailNotifier = mailNotifier;
        this.properties = properties;
    }

    /**
     * 为已注册用户生成验证链接并发送邮件。
     *
     * @param user 目标用户
     */
    public void sendVerificationEmail(AuthUser user) {
        AuthProperties.Email email = properties.getEmail();
        String token = newToken();
        store(VERIFY_PREFIX, token, user.getUserId(), email.getVerificationTokenTtl());
        mailNotifier.sendEmailVerification(user.getEmail(), user.getUsername(), buildLink(VERIFY_PATH, token));
    }

    /**
     * 重新发送验证邮件；邮箱不存在或已验证时静默返回。
     *
     * @param email 邮箱
     */
    public void resendVerificationEmail(String email) {
        AuthUser user = userDetailsService.loadByEmail(email);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        sendVerificationEmail(user);
    }

    /**
     * 消费验证令牌并标记邮箱已验证。
     *
     * @param token 验证令牌
     */
    public void verifyEmail(String token) {
        String userId = consume(VERIFY_PREFIX, token);
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }
        userDetailsService.markEmailVerified(userId);
    }

    /**
     * 发送密码重置邮件；邮箱不存在时静默返回，避免账号枚举。
     *
     * @param email 邮箱
     */
    public void forgotPassword(String email) {
        AuthUser user = userDetailsService.loadByEmail(email);
        if (user == null) {
            return;
        }
        AuthProperties.Email config = properties.getEmail();
        String token = newToken();
        store(RESET_PREFIX, token, user.getUserId(), config.getResetTokenTtl());
        mailNotifier.sendPasswordReset(user.getEmail(), user.getUsername(), buildLink(RESET_PATH, token));
    }

    /**
     * 重置密码：校验令牌单次有效，更新密码并撤销该用户全部令牌。
     *
     * @param token 重置令牌
     * @param newPassword 新密码（明文，服务内编码存储）
     */
    public void resetPassword(String token, String newPassword) {
        String userId = consume(RESET_PREFIX, token);
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }
        AuthUser updated = userDetailsService.updatePassword(userId, newPassword);
        if (updated == null) {
            throw new BizException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }
        tokenService.revokeAllTokens(userId);
    }

    private String consume(String prefix, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key = prefix + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId != null) {
            redisTemplate.delete(key);
        }
        return userId;
    }

    private void store(String prefix, String token, String userId, Duration ttl) {
        redisTemplate.opsForValue().set(prefix + token, userId, ttl);
    }

    private String buildLink(String path, String token) {
        String base = properties.getEmail().getVerifyBaseUrl();
        String separator = base.endsWith("/") ? "" : "/";
        return base + separator + path.substring(1) + "?token=" + token;
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
