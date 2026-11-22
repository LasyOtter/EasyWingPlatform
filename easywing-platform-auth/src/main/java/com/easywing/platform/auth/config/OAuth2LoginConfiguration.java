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

import com.easywing.platform.auth.service.OAuth2ClientRegistrations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 外部 OAuth2/OIDC 登录装配
 * <p>
 * 仅在 {@code easywing.auth.oauth2.enabled=true} 时生效。由配置动态构建
 * {@link ClientRegistrationRepository}，并为授权回调单独建立一条安全链
 * （{@code @Order(1)}），避免影响主链的 JWT 鉴权行为。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "easywing.auth.oauth2", name = "enabled", havingValue = "true")
public class OAuth2LoginConfiguration {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(AuthProperties authProperties) {
        return new InMemoryClientRegistrationRepository(OAuth2ClientRegistrations.fromProperties(
                authProperties.getOAuth2().getProviders()));
    }

    /**
     * OAuth2 登录专用安全链：仅匹配授权发起与回调路径。
     * <p>
     * 该链保留默认 CSRF 防护（授权跳转依赖 state 校验），因此不与主链一样全局禁用。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(HttpSecurity http, OAuth2SuccessHandler successHandler)
            throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(login -> login.successHandler(successHandler));
        return http.build();
    }
}
