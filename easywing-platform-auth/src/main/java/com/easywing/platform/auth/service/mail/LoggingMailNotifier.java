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

import lombok.extern.slf4j.Slf4j;

/**
 * 默认邮件通知器：不真正发送，只把链接写入日志。
 * <p>
 * 邮件能力默认关闭时生效，链接可直接从日志取得，便于本地开发与自动化测试。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class LoggingMailNotifier implements MailNotifier {

    @Override
    public void sendEmailVerification(String to, String username, String verificationLink) {
        log.info("[email:verification] to={} user={} link={}", to, username, verificationLink);
    }

    @Override
    public void sendPasswordReset(String to, String username, String resetLink) {
        log.info("[email:password-reset] to={} user={} link={}", to, username, resetLink);
    }
}
