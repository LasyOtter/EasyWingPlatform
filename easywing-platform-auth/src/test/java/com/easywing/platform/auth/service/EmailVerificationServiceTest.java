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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.service.mail.MailNotifier;
import com.easywing.platform.core.exception.BizException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 邮箱验证 / 密码重置服务单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenService tokenService;

    @Mock
    private MailNotifier mailNotifier;

    private AuthProperties properties;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.getEmail().setVerifyBaseUrl("https://app.example.com/");
        properties.getEmail().setVerificationTokenTtl(Duration.ofHours(24));
        properties.getEmail().setResetTokenTtl(Duration.ofHours(1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service =
                new EmailVerificationService(redisTemplate, userDetailsService, tokenService, mailNotifier, properties);
    }

    private static AuthUser unverified() {
        return new AuthUser(
                "u-1", "alice@example.com", "hash", List.of("ROLE_USER"), "default", "alice@example.com", false);
    }

    @Test
    @DisplayName("发送验证邮件：写入带 TTL 的一次性令牌并投递链接")
    void sendVerificationEmailStoresTokenAndNotifies() {
        service.sendVerificationEmail(unverified());

        verify(valueOperations).set(startsWith("auth:email-verify:"), eq("u-1"), eq(Duration.ofHours(24)));
        verify(mailNotifier)
                .sendEmailVerification(
                        eq("alice@example.com"), eq("alice@example.com"), contains("verify-email?token="));
    }

    @Test
    @DisplayName("验证令牌单次有效：首次标记已验证并删除，再次使用被拒")
    void verifyEmailMarksVerifiedAndConsumesTokenOnce() {
        when(valueOperations.get("auth:email-verify:t-1")).thenReturn("u-1", (String) null);

        service.verifyEmail("t-1");

        verify(userDetailsService).markEmailVerified("u-1");
        verify(redisTemplate).delete("auth:email-verify:t-1");
        assertThatThrownBy(() -> service.verifyEmail("t-1")).isInstanceOf(BizException.class);
        verify(userDetailsService, times(1)).markEmailVerified("u-1");
    }

    @Test
    @DisplayName("空令牌直接拒绝，不触碰存储")
    void verifyEmailRejectsBlankToken() {
        assertThatThrownBy(() -> service.verifyEmail(" ")).isInstanceOf(BizException.class);
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("忘记密码：邮箱不存在时不发信也不写存储，避免账号枚举")
    void forgotPasswordDoesNotLeakUnknownEmail() {
        when(userDetailsService.loadByEmail("nobody@example.com")).thenReturn(null);

        service.forgotPassword("nobody@example.com");

        verifyNoInteractions(valueOperations);
        verifyNoInteractions(mailNotifier);
    }

    @Test
    @DisplayName("忘记密码：写入重置令牌并投递链接")
    void forgotPasswordSendsResetLink() {
        when(userDetailsService.loadByEmail("alice@example.com")).thenReturn(unverified());

        service.forgotPassword("alice@example.com");

        verify(valueOperations).set(startsWith("auth:pwd-reset:"), eq("u-1"), eq(Duration.ofHours(1)));
        verify(mailNotifier)
                .sendPasswordReset(eq("alice@example.com"), eq("alice@example.com"), contains("reset-password?token="));
    }

    @Test
    @DisplayName("重发验证邮件：已验证用户静默跳过")
    void resendSkipsVerifiedUser() {
        AuthUser verified = new AuthUser(
                "u-2", "bob@example.com", "hash", List.of("ROLE_USER"), "default", "bob@example.com", true);
        when(userDetailsService.loadByEmail("bob@example.com")).thenReturn(verified);

        service.resendVerificationEmail("bob@example.com");

        verifyNoInteractions(valueOperations);
        verifyNoInteractions(mailNotifier);
    }

    @Test
    @DisplayName("重置密码：更新密码、删除令牌并撤销该用户全部令牌")
    void resetPasswordUpdatesPasswordAndRevokesAllTokens() {
        when(valueOperations.get("auth:pwd-reset:r-1")).thenReturn("u-1");
        when(userDetailsService.updatePassword("u-1", "a-fresh-password")).thenReturn(unverified());

        service.resetPassword("r-1", "a-fresh-password");

        verify(userDetailsService).updatePassword("u-1", "a-fresh-password");
        verify(redisTemplate).delete("auth:pwd-reset:r-1");
        verify(tokenService).revokeAllTokens("u-1");
    }

    @Test
    @DisplayName("重置密码：令牌无效时拒绝且不撤销任何令牌")
    void resetPasswordRejectsUnknownToken() {
        when(valueOperations.get("auth:pwd-reset:bad")).thenReturn(null);

        assertThatThrownBy(() -> service.resetPassword("bad", "a-fresh-password"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(tokenService);
    }
}
