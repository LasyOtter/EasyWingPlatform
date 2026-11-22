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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.dto.ClientCredentialsRequest;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MachineClientService} 单元测试：凭据校验、scope 过滤与签发参数。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MachineClientServiceTest {

    @Mock
    private TokenService tokenService;

    private AuthProperties properties;
    private MachineClientService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.getOAuth2().setMachineClients(new LinkedHashMap<>());
        AuthProperties.OAuth2.MachineClient client = new AuthProperties.OAuth2.MachineClient();
        client.setEnabled(true);
        client.setClientId("agent-service");
        client.setClientSecret("s3cret");
        client.setScopes(List.of("agent-management", "read"));
        client.setTenant("tenant-a");
        client.setTokenTtl(Duration.ofHours(2));
        properties.getOAuth2().getMachineClients().put("agent-service", client);

        AuthProperties.OAuth2.MachineClient disabled = new AuthProperties.OAuth2.MachineClient();
        disabled.setEnabled(false);
        disabled.setClientId("disabled-client");
        disabled.setClientSecret("s3cret");
        properties.getOAuth2().getMachineClients().put("disabled-client", disabled);

        service = new MachineClientService(properties, tokenService);
    }

    private static ClientCredentialsRequest request(String clientId, String secret, String scope) {
        ClientCredentialsRequest request = new ClientCredentialsRequest();
        request.setClientId(clientId);
        request.setClientSecret(secret);
        request.setScope(scope);
        return request;
    }

    @Test
    @DisplayName("有效凭据 + 授权 scope：签发令牌并透传租户与有效期")
    void issuesTokenForAuthorizedScopes() {
        when(tokenService.issueMachineToken(eq("agent-service"), eq("tenant-a"), any(), any(Duration.class)))
                .thenReturn("jwt");

        TokenResponse response = service.issueClientCredentials(request("agent-service", "s3cret", "read"));

        assertThat(response.getAccessToken()).isEqualTo("jwt");
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getExpiresIn()).isEqualTo(Duration.ofHours(2).getSeconds());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> scopes = ArgumentCaptor.forClass(List.class);
        verify(tokenService)
                .issueMachineToken(eq("agent-service"), eq("tenant-a"), scopes.capture(), eq(Duration.ofHours(2)));
        assertThat(scopes.getValue()).containsExactly("read");
    }

    @Test
    @DisplayName("未指定 scope 时签发客户端授权的全部 scope")
    void defaultsToAllGrantedScopes() {
        when(tokenService.issueMachineToken(eq("agent-service"), eq("tenant-a"), any(), any(Duration.class)))
                .thenReturn("jwt");

        service.issueClientCredentials(request("agent-service", "s3cret", "  "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> scopes = ArgumentCaptor.forClass(List.class);
        verify(tokenService).issueMachineToken(any(), any(), scopes.capture(), any(Duration.class));
        assertThat(scopes.getValue()).containsExactlyInAnyOrder("agent-management", "read");
    }

    @Test
    @DisplayName("错误密钥或未知客户端被拒绝，且不调用签发")
    void rejectsBadCredentials() {
        assertThatThrownBy(() -> service.issueClientCredentials(request("agent-service", "wrong", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> service.issueClientCredentials(request("unknown", "s3cret", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("未启用的客户端被拒绝")
    void rejectsDisabledClient() {
        assertThatThrownBy(() -> service.issueClientCredentials(request("disabled-client", "s3cret", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("缺少 client_id / client_secret 被拒绝")
    void rejectsMissingFields() {
        assertThatThrownBy(() -> service.issueClientCredentials(request(" ", "s3cret", null)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.issueClientCredentials(request("agent-service", " ", null)))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("请求超出授权的 scope 返回 400，且不签发")
    void rejectsUnauthorizedScope() {
        assertThatThrownBy(() -> service.issueClientCredentials(request("agent-service", "s3cret", "admin:write")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("未配置任何机器客户端时统一拒绝")
    void rejectsWhenNoClientsConfigured() {
        properties.getOAuth2().setMachineClients(Map.of());
        MachineClientService emptyService = new MachineClientService(properties, tokenService);

        assertThatThrownBy(() -> emptyService.issueClientCredentials(request("agent-service", "s3cret", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}
