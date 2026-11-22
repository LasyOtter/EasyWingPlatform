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
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 外部身份映射器
 * <p>
 * 将 OAuth2/OIDC 返回的用户属性映射为本地 {@link AuthUser}：以邮箱作为用户名优先，
 * 缺失时回退到 {@code preferred_username}/{@code sub}/显示名。用户首次出现时按配置的
 * 角色与租户供应，已存在则复用，保证同一外部身份稳定对应同一本地用户。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "easywing.auth.oauth2", name = "enabled", havingValue = "true")
public class ExternalIdentityResolver {

    private final UserDetailsService userDetailsService;

    public ExternalIdentityResolver(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * 解析外部身份为本地用户。
     *
     * @param oauth2User OAuth2 用户信息
     * @param provider 提供方配置
     * @param defaultTenant 默认租户
     * @return 本地用户
     */
    public AuthUser resolveOAuth2User(
            OAuth2User oauth2User, AuthProperties.OAuth2.Provider provider, String defaultTenant) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = attribute(attributes, provider.getEmailAttribute());
        String preferredUsername = attribute(attributes, provider.getUserNameAttributeName());
        String displayName = attribute(attributes, provider.getDisplayNameAttribute());
        String username = firstText(email, preferredUsername, displayName, oauth2User.getName());
        if (!StringUtils.hasText(username)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "外部身份缺少可用用户名");
        }
        List<String> roles = provider.getRoles() == null || provider.getRoles().isEmpty()
                ? List.of("ROLE_USER")
                : provider.getRoles();
        return userDetailsService.findOrCreateExternal(username, roles, defaultTenant);
    }

    private static String attribute(Map<String, Object> attributes, String name) {
        if (attributes == null || !StringUtils.hasText(name)) {
            return null;
        }
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }
}
