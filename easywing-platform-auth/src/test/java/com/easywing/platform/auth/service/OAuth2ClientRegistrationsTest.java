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

import com.easywing.platform.auth.config.AuthProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * {@link OAuth2ClientRegistrations} 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class OAuth2ClientRegistrationsTest {

    @Test
    void buildShouldMapAllProviderSettings() {
        ClientRegistration registration = OAuth2ClientRegistrations.build("keycloak", provider());

        assertThat(registration.getRegistrationId()).isEqualTo("keycloak");
        assertThat(registration.getClientId()).isEqualTo("client-id");
        assertThat(registration.getClientSecret()).isEqualTo("client-secret");
        assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(registration.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(registration.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://idp.example.com/authorize");
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("https://idp.example.com/token");
        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                .isEqualTo("https://idp.example.com/userinfo");
        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo("https://idp.example.com/jwks");
        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("sub");
    }

    @Test
    void buildShouldRejectMissingRequiredFields() {
        AuthProperties.OAuth2.Provider provider = provider();
        provider.setClientId(null);

        assertThatThrownBy(() -> OAuth2ClientRegistrations.build("keycloak", provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void fromPropertiesShouldSkipDisabledProviders() {
        Map<String, AuthProperties.OAuth2.Provider> providers = new LinkedHashMap<>();
        providers.put("keycloak", provider());
        AuthProperties.OAuth2.Provider disabled = provider();
        disabled.setEnabled(false);
        providers.put("disabled-idp", disabled);

        List<ClientRegistration> registrations = OAuth2ClientRegistrations.fromProperties(providers);

        assertThat(registrations)
                .extracting(ClientRegistration::getRegistrationId)
                .containsExactly("keycloak");
    }

    @Test
    void fromPropertiesShouldRejectEmptyConfiguration() {
        assertThatThrownBy(() -> OAuth2ClientRegistrations.fromProperties(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("providers");
    }

    private static AuthProperties.OAuth2.Provider provider() {
        AuthProperties.OAuth2.Provider provider = new AuthProperties.OAuth2.Provider();
        provider.setClientId("client-id");
        provider.setClientSecret("client-secret");
        provider.setAuthorizationUri("https://idp.example.com/authorize");
        provider.setTokenUri("https://idp.example.com/token");
        provider.setUserInfoUri("https://idp.example.com/userinfo");
        provider.setJwkSetUri("https://idp.example.com/jwks");
        provider.setIssuerUri("https://idp.example.com");
        provider.setScopes(List.of("openid", "profile", "email"));
        provider.setUserNameAttributeName("sub");
        return provider;
    }
}
