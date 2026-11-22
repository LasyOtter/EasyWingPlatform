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
package com.easywing.platform.auth.config;

import com.easywing.platform.auth.service.mail.LoggingMailNotifier;
import com.easywing.platform.auth.service.mail.MailNotifier;
import com.easywing.platform.auth.service.mail.SmtpMailNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 邮件通知器装配
 * <p>
 * 默认（{@code easywing.auth.email.enabled=false}）注册 {@link LoggingMailNotifier}；
 * 开启后改用 {@link SmtpMailNotifier}，未配置 SMTP 主机时启动即失败并给出明确提示。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
public class MailNotifierConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "easywing.auth.email", name = "enabled", havingValue = "true")
    public MailNotifier smtpMailNotifier(AuthProperties properties) {
        AuthProperties.Email email = properties.getEmail();
        return new SmtpMailNotifier(
                email.getSmtpHost(),
                email.getSmtpPort(),
                email.getSmtpUsername(),
                email.getSmtpPassword(),
                email.isSmtpStartTls(),
                email.getFrom());
    }

    @Bean
    @ConditionalOnMissingBean(MailNotifier.class)
    public MailNotifier loggingMailNotifier() {
        return new LoggingMailNotifier();
    }
}
