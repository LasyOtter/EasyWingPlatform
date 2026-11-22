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

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 基于 SMTP 的邮件通知器
 * <p>
 * 直接使用 {@code easywing.auth.email.smtp.*} 配置构造会话，不依赖
 * {@code spring.mail.*}，避免同一份配置出现两处来源。凭据只经配置/环境变量注入。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class SmtpMailNotifier implements MailNotifier {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean startTls;
    private final String from;
    private final Session session;

    public SmtpMailNotifier(String host, int port, String username, String password, boolean startTls, String from) {
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("已开启邮箱功能但缺少 SMTP 主机：请配置 easywing.auth.email.smtp-host");
        }
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.startTls = startTls;
        this.from = from;
        this.session = buildSession();
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        boolean auth = StringUtils.hasText(username);
        props.put("mail.smtp.auth", String.valueOf(auth));
        if (!auth) {
            return Session.getInstance(props);
        }
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    @Override
    public void sendEmailVerification(String to, String username, String verificationLink) {
        String subject = "【EasyWing】请验证您的邮箱";
        String body =
                """
                <p>您好 %s，</p>
                <p>感谢您注册 EasyWing 平台。请点击下方链接完成邮箱验证（24 小时内有效）：</p>
                <p><a href="%s">%s</a></p>
                <p>如果这不是您的操作，请忽略本邮件。</p>
                """
                        .formatted(safe(username), verificationLink, verificationLink);
        send(to, subject, body);
    }

    @Override
    public void sendPasswordReset(String to, String username, String resetLink) {
        String subject = "【EasyWing】密码重置申请";
        String body =
                """
                <p>您好 %s，</p>
                <p>我们收到了您的密码重置请求。请点击下方链接设置新密码（1 小时内有效）：</p>
                <p><a href="%s">%s</a></p>
                <p>如果这不是您的操作，请忽略本邮件，您的密码将保持不变。</p>
                """
                        .formatted(safe(username), resetLink, resetLink);
        send(to, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlBody, "text/html;charset=UTF-8");
            Transport.send(message);
            log.info("邮件已发送: to={} subject={}", to, subject);
        } catch (MessagingException ex) {
            throw new IllegalStateException("发送邮件失败: " + to, ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
