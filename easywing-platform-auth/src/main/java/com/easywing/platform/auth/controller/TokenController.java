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

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.ClientCredentialsRequest;
import com.easywing.platform.auth.dto.LoginRequest;
import com.easywing.platform.auth.dto.RefreshRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.service.EmailVerificationService;
import com.easywing.platform.auth.service.MachineClientService;
import com.easywing.platform.auth.service.TokenService;
import com.easywing.platform.auth.service.UserDetailsService;
import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.web.ratelimit.RateLimit;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 令牌管理控制器
 * <p>
 * 提供以下端点：
 * <ul>
 *     <li>POST /api/v1/token/login - 用户名密码登录，签发令牌对</li>
 *     <li>POST /api/v1/token/refresh - 使用刷新令牌换取新的令牌对</li>
 *     <li>POST /api/v1/token/logout - 注销当前令牌</li>
 *     <li>POST /api/v1/token/revoke - 撤销当前令牌</li>
 *     <li>POST /api/v1/token/revoke-all - 撤销用户所有令牌</li>
 *     <li>POST /api/v1/token/email/verify - 消费验证令牌，标记邮箱已验证</li>
 *     <li>POST /api/v1/token/email/resend - 重发验证邮件（不泄露邮箱是否存在）</li>
 *     <li>POST /api/v1/token/password/forgot - 发送密码重置邮件（不泄露邮箱是否存在）</li>
 *     <li>POST /api/v1/token/password/reset - 重置密码并撤销全部令牌</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/token")
@ApiVersion("v1")
@Tag(name = "Token", description = "JWT令牌管理接口（v1）")
public class TokenController {

    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;
    private final AuthProperties authProperties;
    private final EmailVerificationService emailVerificationService;
    private final MachineClientService machineClientService;

    public TokenController(
            TokenService tokenService,
            UserDetailsService userDetailsService,
            AuthProperties authProperties,
            EmailVerificationService emailVerificationService,
            MachineClientService machineClientService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.authProperties = authProperties;
        this.emailVerificationService = emailVerificationService;
        this.machineClientService = machineClientService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，成功后签发访问令牌和刷新令牌")
    @RateLimit(key = "#request.username", rate = 5, capacity = 5, message = "登录尝试过于频繁，请5分钟后再试")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthUser user = userDetailsService.loadByUsername(request.getUsername());
        if (!user.isEnabled()) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "账户已锁定");
        }
        if (!userDetailsService.validatePassword(user, request.getPassword())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        // 放在密码校验之后，避免通过该分支枚举邮箱是否已完成验证
        if (isEmailVerificationRequired() && !user.isEmailVerified()) {
            throw new BizException(ErrorCode.EMAIL_NOT_VERIFIED, "邮箱尚未验证，请先查收验证邮件完成验证");
        }
        TokenResponse response = tokenService.issueTokenPair(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/client-credentials")
    @Operation(
            summary = "机器客户端令牌",
            description = "client_credentials（RFC 6749 §4.4 等价实现）：校验 deployment-bound 客户端凭据并按授权 scope 签发访问令牌")
    @RateLimit(key = "#request.clientId", rate = 20, capacity = 20, message = "令牌申请过于频繁，请稍后再试")
    public ResponseEntity<TokenResponse> clientCredentials(@Valid @RequestBody ClientCredentialsRequest request) {
        return ResponseEntity.ok(machineClientService.issueClientCredentials(request));
    }

    @PostMapping("/register")
    @Operation(summary = "邮箱注册", description = "仅在 ALLOW_REGISTRATION=true 时开放；要求邮箱验证时先发送验证邮件，验证通过后才可登录")
    @RateLimit(key = "#request.email", rate = 3, capacity = 3, message = "注册尝试过于频繁，请稍后再试")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        if (!authProperties.getRegistration().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前服务未开放自助注册");
        }
        boolean requireVerification = isEmailVerificationRequired();
        AuthUser user;
        try {
            user = userDetailsService.register(
                    request.getEmail(), request.getPassword(), !requireVerification, defaultTenant());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        if (requireVerification) {
            emailVerificationService.sendVerificationEmail(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "注册成功，验证邮件已发送，完成验证后即可登录", "email", request.getEmail()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenService.issueTokenPair(user));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "验证邮箱", description = "消费验证令牌，标记邮箱已验证；令牌单次有效")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody EmailTokenRequest request) {
        emailVerificationService.verifyEmail(request.getToken());
        return ResponseEntity.ok(Map.of("message", "邮箱验证成功，现在可以登录"));
    }

    @PostMapping("/email/resend")
    @Operation(summary = "重发验证邮件", description = "邮箱不存在或已验证时同样返回成功，避免账号枚举")
    @RateLimit(key = "#request.email", rate = 3, capacity = 3, message = "请求过于频繁，请稍后再试")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "如该邮箱已注册且尚未验证，验证邮件已发送"));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "忘记密码", description = "发送密码重置邮件；邮箱不存在时同样返回成功，避免账号枚举")
    @RateLimit(key = "#request.email", rate = 3, capacity = 3, message = "请求过于频繁，请稍后再试")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "如该邮箱已注册，密码重置邮件已发送"));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "重置密码", description = "校验重置令牌并更新密码，成功后撤销该用户全部令牌")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        emailVerificationService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "密码重置成功，请使用新密码重新登录"));
    }

    private boolean isEmailVerificationRequired() {
        AuthProperties.Registration registration = authProperties.getRegistration();
        return registration != null && registration.isRequireEmailVerification();
    }

    private String defaultTenant() {
        AuthProperties.Registration registration = authProperties.getRegistration();
        return registration != null && StringUtils.hasText(registration.getDefaultTenant())
                ? registration.getDefaultTenant()
                : "default";
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

    @PostMapping("/revoke")
    @Operation(summary = "撤销当前Token", description = "将当前令牌加入黑名单使其立即失效（与logout等价）")
    public ResponseEntity<Void> revoke(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenService.revokeToken(token);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke-all")
    @Operation(summary = "撤销所有Token", description = "撤销当前用户的所有令牌，包括所有设备登录的令牌")
    public ResponseEntity<Void> revokeAll(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // 从令牌自身解析用户ID，不能信任客户端传入的 X-User-Id，否则可越权撤销他人会话
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = tokenService.resolveUserId(authHeader.substring(7));
        tokenService.revokeAllTokens(userId);
        return ResponseEntity.noContent().build();
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RegistrationRequest {
        @NotBlank
        @Email
        @Size(max = 254)
        private String email;

        @NotBlank
        @Size(min = 12, max = 128)
        private String password;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmailTokenRequest {
        @NotBlank
        private String token;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmailRequest {
        @NotBlank
        @Email
        @Size(max = 254)
        private String email;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PasswordResetRequest {
        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 12, max = 128)
        private String newPassword;
    }
}
