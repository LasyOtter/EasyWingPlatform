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
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.service.LdapAuthService;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.web.ratelimit.RateLimit;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LDAP 登录控制器
 * <p>
 * 提供 {@code POST /api/v1/token/ldap/login}，以 LDAP 绑定方式校验凭据并签发平台令牌。
 * 仅当 {@code easywing.auth.ldap.enabled=true} 时注册该端点，默认关闭。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/token/ldap")
@ConditionalOnProperty(prefix = "easywing.auth.ldap", name = "enabled", havingValue = "true")
@ApiVersion("v1")
@Tag(name = "LDAP Auth", description = "LDAP 绑定认证接口（v1，需 easywing.auth.ldap.enabled=true）")
public class LdapLoginController {

    private final LdapAuthService ldapAuthService;
    private final TokenService tokenService;

    public LdapLoginController(LdapAuthService ldapAuthService, TokenService tokenService) {
        this.ldapAuthService = ldapAuthService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "LDAP 登录", description = "使用 LDAP 凭据绑定认证，成功后签发访问令牌和刷新令牌")
    @RateLimit(key = "#request.username", rate = 5, capacity = 5, message = "LDAP 登录尝试过于频繁，请稍后再试")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LdapLoginRequest request) {
        AuthUser user = ldapAuthService.authenticate(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(tokenService.issueTokenPair(user));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LdapLoginRequest {
        @NotBlank
        @Size(max = 254)
        private String username;

        @NotBlank
        @Size(min = 1, max = 128)
        private String password;
    }
}
