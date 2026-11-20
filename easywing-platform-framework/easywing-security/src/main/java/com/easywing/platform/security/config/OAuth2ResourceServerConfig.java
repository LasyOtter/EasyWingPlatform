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
package com.easywing.platform.security.config;

import com.easywing.platform.security.converter.JwtAuthConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * OAuth2.1资源服务器安全配置（框架内置实现）
 * <p>
 * 支持 JWT 和不透明令牌两种模式。
 * <p>
 * <b>不参与自动装配。</b> starter
 * {@code easywing-boot-starter-security-oauth2} 提供功能等价、且与认证服务
 * claim 约定一致的实现（权限前缀为空，匹配 {@code roles} claim）。两者同时自动
 * 装配会注册同名 {@code securityFilterChain} 导致上下文启动失败。
 * <p>
 * 本配置供需要 {@code ROLE_} 前缀或自定义 {@code public-paths} 语义的场景
 * 通过 {@code @Import} 显式启用。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(EasyWingSecurityProperties.class)
@ConditionalOnProperty(prefix = "easywing.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OAuth2ResourceServerConfig {

    private final EasyWingSecurityProperties securityProperties;

    public OAuth2ResourceServerConfig(EasyWingSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 10)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（无状态API）
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用Form登录
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用HTTP Basic
                .httpBasic(AbstractHttpConfigurer::disable)
                // 配置会话管理：无状态
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 配置授权规则
                .authorizeHttpRequests(authorize -> {
                    // 公共路径
                    for (String path : securityProperties.getPublicPaths()) {
                        authorize.requestMatchers(path).permitAll();
                    }
                    // 其他请求需要认证
                    authorize.anyRequest().authenticated();
                })
                // 配置OAuth2资源服务器
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                );

        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName(
                securityProperties.getOauth2().getJwt().getAuthoritiesClaimName());
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        authenticationConverter.setPrincipalClaimName(securityProperties.getOauth2().getJwt().getUsernameClaimName());

        return authenticationConverter;
    }

    @Bean
    @ConditionalOnBean(JwtAuthenticationConverter.class)
    @ConditionalOnMissingBean
    public JwtAuthConverter jwtAuthConverter() {
        return new JwtAuthConverter(securityProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
