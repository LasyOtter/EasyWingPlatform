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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * {@link ExternalIdentityResolver} 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ExternalIdentityResolverTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Test
    void shouldPreferEmailAsUsername() {
        AuthProperties.OAuth2.Provider provider = provider();
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "alice@example.com", "sub", "subject-1", "name", "Alice"),
                "sub");
        AuthUser expected = new AuthUser("u-1", "alice@example.com", null, List.of("ROLE_USER"), "tenant-1");
        when(userDetailsService.findOrCreateExternal("alice@example.com", List.of("ROLE_USER"), "tenant-1"))
                .thenReturn(expected);

        AuthUser actual =
                new ExternalIdentityResolver(userDetailsService).resolveOAuth2User(oauth2User, provider, "tenant-1");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldFallbackToSubjectWhenEmailMissing() {
        AuthProperties.OAuth2.Provider provider = provider();
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "subject-1", "name", "Alice"), "sub");
        when(userDetailsService.findOrCreateExternal("subject-1", List.of("ROLE_USER"), "tenant-1"))
                .thenReturn(new AuthUser("u-1", "subject-1", null, List.of("ROLE_USER"), "tenant-1"));

        AuthUser actual =
                new ExternalIdentityResolver(userDetailsService).resolveOAuth2User(oauth2User, provider, "tenant-1");

        assertThat(actual.getUsername()).isEqualTo("subject-1");
    }

    @Test
    void shouldUseDefaultRolesWhenProviderRolesEmpty() {
        AuthProperties.OAuth2.Provider provider = provider();
        provider.setRoles(List.of());
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "bob@example.com", "sub", "subject-2"),
                "sub");
        when(userDetailsService.findOrCreateExternal("bob@example.com", List.of("ROLE_USER"), "tenant-1"))
                .thenReturn(new AuthUser("u-2", "bob@example.com", null, List.of("ROLE_USER"), "tenant-1"));

        AuthUser actual =
                new ExternalIdentityResolver(userDetailsService).resolveOAuth2User(oauth2User, provider, "tenant-1");

        assertThat(actual.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    void shouldRejectIdentityWithoutAnyUsername() {
        AuthProperties.OAuth2.Provider provider = provider();
        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttributes()).thenReturn(Map.of());
        when(oauth2User.getName()).thenReturn(null);

        assertThatThrownBy(() -> new ExternalIdentityResolver(userDetailsService)
                        .resolveOAuth2User(oauth2User, provider, "tenant-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    private static AuthProperties.OAuth2.Provider provider() {
        AuthProperties.OAuth2.Provider provider = new AuthProperties.OAuth2.Provider();
        provider.setEmailAttribute("email");
        provider.setUserNameAttributeName("sub");
        provider.setDisplayNameAttribute("name");
        provider.setRoles(List.of("ROLE_USER"));
        return provider;
    }
}
