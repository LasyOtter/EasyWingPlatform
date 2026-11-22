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
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/**
 * OAuth2 客户端注册构建器
 * <p>
 * 由配置显式构建 {@link ClientRegistration}，不进行 issuer 元数据自动发现，
 * 避免启动期对外网产生强依赖。授权码模式，回调地址固定为
 * {@code {baseUrl}/login/oauth2/code/{registrationId}}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class OAuth2ClientRegistrations {

    private OAuth2ClientRegistrations() {}

    /**
     * 构建单个客户端注册。
     *
     * @param registrationId 注册标识，例如 {@code keycloak}
     * @param provider 提供方配置
     * @return 客户端注册
     */
    public static ClientRegistration build(String registrationId, AuthProperties.OAuth2.Provider provider) {
        requireText(provider.getClientId(), "clientId", registrationId);
        requireText(provider.getClientSecret(), "clientSecret", registrationId);
        requireText(provider.getAuthorizationUri(), "authorizationUri", registrationId);
        requireText(provider.getTokenUri(), "tokenUri", registrationId);
        requireText(provider.getUserInfoUri(), "userInfoUri", registrationId);

        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(provider.getScopes())
                .authorizationUri(provider.getAuthorizationUri())
                .tokenUri(provider.getTokenUri())
                .userInfoUri(provider.getUserInfoUri())
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName(provider.getUserNameAttributeName())
                .clientName(registrationId);
        if (StringUtils.hasText(provider.getJwkSetUri())) {
            builder.jwkSetUri(provider.getJwkSetUri());
        }
        if (StringUtils.hasText(provider.getIssuerUri())) {
            builder.issuerUri(provider.getIssuerUri());
        }
        return builder.build();
    }

    /**
     * 从配置中提取所有启用的提供方。
     *
     * @param authProperties 认证配置
     * @return 客户端注册列表
     */
    public static List<ClientRegistration> fromProperties(
            java.util.Map<String, AuthProperties.OAuth2.Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException("easywing.auth.oauth2.enabled=true 但未配置 providers");
        }
        List<ClientRegistration> registrations = providers.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
                .map(entry -> build(entry.getKey(), entry.getValue()))
                .toList();
        if (registrations.isEmpty()) {
            throw new IllegalStateException("easywing.auth.oauth2.enabled=true 但没有启用的 provider");
        }
        return registrations;
    }

    private static void requireText(String value, String field, String registrationId) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "OAuth2 provider '" + registrationId + "' 缺少必填配置 easywing.auth.oauth2." + field);
        }
    }
}
