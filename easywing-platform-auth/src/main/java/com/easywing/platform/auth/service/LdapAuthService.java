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
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import javax.naming.directory.DirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * LDAP 绑定认证服务
 * <p>
 * 先按 {@code userSearchFilter} 定位用户条目，再以该条目 DN 与用户密码执行绑定，
 * 绑定成功即认证通过。认证通过后按配置的角色/租户在本地供应用户，并签发平台 JWT。
 * <p>
 * 仅在 {@code easywing.auth.ldap.enabled=true} 时装配。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "easywing.auth.ldap", name = "enabled", havingValue = "true")
public class LdapAuthService {

    private final ContextSource contextSource;
    private final LdapTemplate ldapTemplate;
    private final UserDetailsService userDetailsService;
    private final AuthProperties.Ldap ldap;

    public LdapAuthService(
            ContextSource contextSource,
            LdapTemplate ldapTemplate,
            UserDetailsService userDetailsService,
            AuthProperties authProperties) {
        this.contextSource = contextSource;
        this.ldapTemplate = ldapTemplate;
        this.userDetailsService = userDetailsService;
        this.ldap = authProperties.getLdap();
    }

    /**
     * 校验 LDAP 凭据并返回本地用户。
     *
     * @param username LDAP 登录名
     * @param rawPassword 明文密码
     * @return 本地供应的用户
     */
    public AuthUser authenticate(String username, String rawPassword) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        String loginName = username.trim();
        LdapEntry entry = findEntry(loginName);
        if (entry == null || !StringUtils.hasText(entry.dn())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        bind(entry.dn(), rawPassword);
        return userDetailsService.findOrCreateExternal(loginName, ldap.getDefaultRoles(), ldap.getDefaultTenant());
    }

    private LdapEntry findEntry(String username) {
        String filter = LdapFilter.build(ldap.getUserSearchFilter(), username);
        try {
            return ldapTemplate.searchForObject(
                    ldap.getUserSearchBase(), filter, (ContextMapper<LdapEntry>) this::toEntry);
        } catch (IncorrectResultSizeDataAccessException ex) {
            log.debug("LDAP search returned no entry for filter {}", filter);
            return null;
        }
    }

    private LdapEntry toEntry(Object context) {
        DirContextAdapter adapter = (DirContextAdapter) context;
        return new LdapEntry(
                adapter.getNameInNamespace(),
                attribute(adapter, ldap.getEmailAttribute()),
                attribute(adapter, ldap.getDisplayNameAttribute()));
    }

    private static String attribute(DirContextAdapter adapter, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String value = adapter.getStringAttribute(name);
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 以用户 DN 执行绑定；失败统一转换为凭据错误，不泄露具体原因。
     */
    private void bind(String dn, String rawPassword) {
        DirContext ignored = null;
        try {
            ignored = contextSource.getContext(dn, rawPassword);
        } catch (Exception ex) {
            log.debug("LDAP bind failed for dn={}: {}", dn, ex.getMessage());
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        } finally {
            if (ignored != null) {
                try {
                    ignored.close();
                } catch (Exception ex) {
                    log.debug("Closing LDAP context failed: {}", ex.getMessage());
                }
            }
        }
    }
}
