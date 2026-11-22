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
import com.easywing.platform.auth.dto.ClientCredentialsRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 机器客户端（client_credentials）认证服务。
 * <p>
 * 用于 deployment-bound 的服务身份：按 {@code easywing.auth.oauth2.machine-clients} 校验
 * client-id/client-secret，并按客户端授权的 scope 集合签发访问令牌。
 * <ul>
 *     <li>密钥比较使用常量时间算法，避免通过响应时间猜测；</li>
 *     <li>client-id 不存在、未启用或密钥错误统一返回同一错误，避免客户端枚举；</li>
 *     <li>请求的 scope 必须是客户端授权 scope 的子集，否则返回 400。</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class MachineClientService {

    private static final String DEFAULT_TENANT = "default";

    private final AuthProperties properties;
    private final TokenService tokenService;

    public MachineClientService(AuthProperties properties, TokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
    }

    /**
     * 校验客户端凭据并签发访问令牌。
     *
     * @param request client_credentials 请求
     * @return 访问令牌（无刷新令牌）
     */
    public TokenResponse issueClientCredentials(ClientCredentialsRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getClientId())
                || !StringUtils.hasText(request.getClientSecret())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "缺少 client_id 或 client_secret");
        }

        AuthProperties.OAuth2 oauth2 = properties.getOAuth2();
        Map<String, AuthProperties.OAuth2.MachineClient> clients =
                oauth2 == null || oauth2.getMachineClients() == null ? Map.of() : oauth2.getMachineClients();

        AuthProperties.OAuth2.MachineClient matched = null;
        for (AuthProperties.OAuth2.MachineClient client : clients.values()) {
            if (client == null || !client.isEnabled()) {
                continue;
            }
            if (!request.getClientId().equals(client.getClientId())) {
                continue;
            }
            // 常量时间比较，避免基于响应时间的密钥探测
            if (secretEquals(request.getClientSecret(), client.getClientSecret())) {
                matched = client;
                break;
            }
        }
        if (matched == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "客户端凭据无效或客户端未启用");
        }

        List<String> scopes = resolveScopes(request.getScope(), matched.getScopes());
        Duration ttl = matched.getTokenTtl() == null
                        || matched.getTokenTtl().isZero()
                        || matched.getTokenTtl().isNegative()
                ? Duration.ofHours(1)
                : matched.getTokenTtl();
        String tenant = StringUtils.hasText(matched.getTenant()) ? matched.getTenant() : DEFAULT_TENANT;

        String token = tokenService.issueMachineToken(matched.getClientId(), tenant, scopes, ttl);
        return new TokenResponse(token, null, ttl.getSeconds());
    }

    /**
     * 计算实际签发的 scope：请求为空时签发客户端授权的全部 scope；
     * 否则请求集合必须是授权集合的子集。
     */
    private static List<String> resolveScopes(String requested, List<String> allowed) {
        Set<String> granted = new LinkedHashSet<>();
        if (allowed != null) {
            allowed.stream().filter(StringUtils::hasText).map(String::trim).forEach(granted::add);
        }
        Set<String> requestedScopes = new LinkedHashSet<>();
        if (StringUtils.hasText(requested)) {
            for (String part : requested.trim().split("\\s+")) {
                if (StringUtils.hasText(part)) {
                    requestedScopes.add(part.trim());
                }
            }
        }
        if (requestedScopes.isEmpty()) {
            return new ArrayList<>(granted);
        }
        if (!granted.containsAll(requestedScopes)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "请求的 scope 未被该客户端授权");
        }
        return new ArrayList<>(requestedScopes);
    }

    private static boolean secretEquals(String provided, String expected) {
        if (expected == null) {
            // 仍执行一次比较以保持时间特征一致
            MessageDigest digest = newSha256();
            digest.digest(provided.getBytes(StandardCharsets.UTF_8));
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
