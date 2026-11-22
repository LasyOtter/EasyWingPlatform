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
package com.easywing.platform.auth.service.mail;

/**
 * 邮件通知器
 * <p>
 * 邮箱验证与密码重置链接通过该接口投递。未开启 SMTP 时使用
 * {@link LoggingMailNotifier}（仅写日志，便于本地开发与自动化测试），
 * 开启 {@code easywing.auth.email.enabled=true} 后切换为 {@link SmtpMailNotifier}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface MailNotifier {

    /**
     * 发送邮箱验证邮件。
     *
     * @param to 收件人
     * @param username 用户名（用于邮件正文展示）
     * @param verificationLink 验证链接
     */
    void sendEmailVerification(String to, String username, String verificationLink);

    /**
     * 发送密码重置邮件。
     *
     * @param to 收件人
     * @param username 用户名（用于邮件正文展示）
     * @param resetLink 重置链接
     */
    void sendPasswordReset(String to, String username, String resetLink);
}
