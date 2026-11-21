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
package com.easywing.platform.oauth2.config;

import com.easywing.platform.security.config.EasyWingSecurityProperties;
import com.easywing.platform.security.config.JwtAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * OAuth2 资源服务器自动配置
 * <p>
 * 提供 JWT 认证和权限解析能力
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
@EnableWebSecurity
@EnableConfigurationProperties({OAuth2Properties.class, EasyWingSecurityProperties.class})
@ConditionalOnProperty(prefix = "easywing.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableMethodSecurity(prePostEnabled = true)
public class OAuth2ResourceServerAutoConfiguration {

    /**
     * 配置 JWT 解码器
     * <p>
     * 项目通过 {@code easywing.security.oauth2.jwt.jwk-set-uri} 指定公钥来源，
     * 而不是 Spring Boot 默认的 {@code spring.security.oauth2.resourceserver.jwt.*}。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(OAuth2Properties properties) {
        if (StringUtils.hasText(properties.getJwkSetUri())) {
            log.info("JWT decoder using jwk-set-uri: {}", properties.getJwkSetUri());
            return NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        }
        if (StringUtils.hasText(properties.getIssuerUri())) {
            log.info("JWT decoder using issuer-uri: {}", properties.getIssuerUri());
            return NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri())
                    .build();
        }
        throw new IllegalStateException("easywing.security.oauth2.jwt.jwk-set-uri or issuer-uri must be configured");
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            OAuth2Properties properties,
            EasyWingSecurityProperties securityProperties)
            throws Exception {
        log.info("EasyWing OAuth2 Resource Server SecurityFilterChain configured");
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    for (String path : securityProperties.getPublicPaths()) {
                        auth.requestMatchers(path).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter(properties)))
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter(OAuth2Properties properties) {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // 必须与认证服务签发令牌时使用的 claim 名一致（AuthProperties.Jwt.rolesClaimName）
        grantedAuthoritiesConverter.setAuthoritiesClaimName(properties.getAuthoritiesClaimName());
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName(properties.getUsernameClaimName());
        return jwtAuthenticationConverter;
    }

    public OAuth2ResourceServerAutoConfiguration() {
        log.info("EasyWing OAuth2 Resource Server AutoConfiguration initialized");
    }
}
