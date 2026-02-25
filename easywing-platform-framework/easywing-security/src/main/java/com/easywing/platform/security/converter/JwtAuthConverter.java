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
package com.easywing.platform.security.converter;

import com.easywing.platform.security.config.EasyWingSecurityProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT认证转换器
 * <p>
 * 从JWT令牌中提取用户信息和权限，转换为认证对象
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EasyWingSecurityProperties properties;

    public JwtAuthConverter(EasyWingSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(properties.getOauth2().getJwt().getUsernameClaimName()));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        EasyWingSecurityProperties.OAuth2ResourceServer.JwtConfig jwtConfig = properties.getOauth2().getJwt();
        String claimName = jwtConfig.getAuthoritiesClaimName();

        List<String> roles = jwt.getClaimAsStringList(claimName);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
