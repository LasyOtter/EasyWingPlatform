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
package com.easywing.platform.auth.controller;

import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.LoginRequest;
import com.easywing.platform.auth.dto.RefreshRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.auth.service.UserDetailsService;
import com.easywing.platform.core.constant.HttpHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌管理控制器
 * <p>
 * 提供以下端点：
 * <ul>
 *     <li>POST /token/login - 用户名密码登录，签发令牌对</li>
 *     <li>POST /token/refresh - 使用刷新令牌换取新的令牌对</li>
 *     <li>POST /token/logout - 注销当前令牌</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/token")
@Tag(name = "Token", description = "JWT令牌管理接口")
public class TokenController {

    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;

    public TokenController(TokenService tokenService, UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，成功后签发访问令牌和刷新令牌")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthUser user = userDetailsService.loadByUsername(request.getUsername());
        if (!user.isEnabled()) {
            return ResponseEntity.status(403).build();
        }
        if (!userDetailsService.validatePassword(user, request.getPassword())) {
            return ResponseEntity.status(401).build();
        }
        TokenResponse response = tokenService.issueTokenPair(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用刷新令牌换取新的访问令牌和刷新令牌")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = tokenService.refreshTokenPair(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "注销登录", description = "将当前令牌加入黑名单使其立即失效")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenService.revokeToken(token);
        }
        return ResponseEntity.noContent().build();
    }
}
