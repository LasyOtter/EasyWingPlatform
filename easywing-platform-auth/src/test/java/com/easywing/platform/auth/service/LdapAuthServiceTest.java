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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import javax.naming.directory.DirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;

/**
 * {@link LdapAuthService} 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LdapAuthServiceTest {

    private static final String DN = "uid=alice,ou=people,dc=example,dc=com";

    @Mock
    private ContextSource contextSource;

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private UserDetailsService userDetailsService;

    private AuthProperties authProperties;
    private LdapAuthService service;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.getLdap().setUserSearchBase("ou=people,dc=example,dc=com");
        authProperties.getLdap().setUserSearchFilter("(uid={0})");
        authProperties.getLdap().setDefaultRoles(java.util.List.of("ROLE_USER"));
        authProperties.getLdap().setDefaultTenant("tenant-1");
        service = new LdapAuthService(contextSource, ldapTemplate, userDetailsService, authProperties);
    }

    @Test
    void authenticateShouldProvisionLocalUserAfterSuccessfulBind() {
        LdapEntry entry = new LdapEntry(DN, "alice@example.com", "Alice");
        when(ldapTemplate.searchForObject(anyString(), anyString(), any(ContextMapper.class)))
                .thenReturn(entry);
        DirContext dirContext = mock(DirContext.class);
        when(contextSource.getContext(DN, "secret")).thenReturn(dirContext);
        AuthUser expected = new AuthUser("u-1", "alice", null, java.util.List.of("ROLE_USER"), "tenant-1");
        when(userDetailsService.findOrCreateExternal("alice", java.util.List.of("ROLE_USER"), "tenant-1"))
                .thenReturn(expected);

        AuthUser actual = service.authenticate("alice", "secret");

        assertThat(actual).isSameAs(expected);
        verify(contextSource).getContext(DN, "secret");
    }

    @Test
    void authenticateShouldRejectWrongPassword() {
        LdapEntry entry = new LdapEntry(DN, "alice@example.com", "Alice");
        when(ldapTemplate.searchForObject(anyString(), anyString(), any(ContextMapper.class)))
                .thenReturn(entry);
        when(contextSource.getContext(DN, "wrong"))
                .thenThrow(new org.springframework.ldap.AuthenticationException(
                        new javax.naming.AuthenticationException("bad credentials")));

        assertThatThrownBy(() -> service.authenticate("alice", "wrong"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void authenticateShouldRejectUnknownUser() {
        when(ldapTemplate.searchForObject(anyString(), anyString(), any(ContextMapper.class)))
                .thenThrow(new IncorrectResultSizeDataAccessException(1));

        assertThatThrownBy(() -> service.authenticate("ghost", "secret"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void authenticateShouldRejectBlankCredentialsWithoutTouchingDirectory() {
        assertThatThrownBy(() -> service.authenticate("  ", "secret"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(ldapTemplate, contextSource);
    }
}
