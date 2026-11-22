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

import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.service.ExternalIdentityResolver;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 登录成功处理器
 * <p>
 * 将外部身份映射为本地用户并签发平台 JWT，以 JSON 形式直接返回令牌对，
 * 便于前端/CLI 直接消费，无需再跳转登录页。响应带 {@code Cache-Control: no-store}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "easywing.auth.oauth2", name = "enabled", havingValue = "true")
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final TokenService tokenService;
    private final ExternalIdentityResolver identityResolver;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public OAuth2SuccessHandler(
            TokenService tokenService,
            ExternalIdentityResolver identityResolver,
            AuthProperties authProperties,
            ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.identityResolver = identityResolver;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)
                || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new IllegalStateException("Unexpected authentication type: " + authentication.getClass());
        }
        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
        AuthProperties.OAuth2.Provider provider =
                authProperties.getOAuth2().getProviders().get(registrationId);
        if (provider == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "未知的 OAuth2 提供方: " + registrationId);
        }
        AuthUser user = identityResolver.resolveOAuth2User(
                oauth2User, provider, authProperties.getOAuth2().getDefaultTenant());
        TokenResponse tokenResponse = tokenService.issueTokenPair(user);
        log.info("OAuth2 login success: provider={}, username={}", registrationId, user.getUsername());

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), tokenResponse);
    }
}
