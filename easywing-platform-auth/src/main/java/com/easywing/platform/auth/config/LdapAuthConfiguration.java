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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.util.StringUtils;

/**
 * LDAP 认证装配
 * <p>
 * 仅在 {@code easywing.auth.ldap.enabled=true} 时创建目录源与 {@link LdapTemplate}。
 * 未配置 managerDn 时以匿名方式读取目录（仅用于用户搜索，绑定仍使用用户自身凭据）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "easywing.auth.ldap", name = "enabled", havingValue = "true")
public class LdapAuthConfiguration {

    @Bean
    public ContextSource ldapContextSource(AuthProperties authProperties) {
        AuthProperties.Ldap ldap = authProperties.getLdap();
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldap.getUrl());
        if (StringUtils.hasText(ldap.getBaseDn())) {
            contextSource.setBase(ldap.getBaseDn());
        }
        if (StringUtils.hasText(ldap.getManagerDn())) {
            contextSource.setUserDn(ldap.getManagerDn());
            contextSource.setPassword(ldap.getManagerPassword());
        }
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate(ContextSource ldapContextSource) {
        return new LdapTemplate(ldapContextSource);
    }
}
