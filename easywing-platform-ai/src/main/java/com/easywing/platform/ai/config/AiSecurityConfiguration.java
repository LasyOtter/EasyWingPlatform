package com.easywing.platform.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * AI 服务安全装配：在既有 authorities 映射之上补充 {@code scope -> SCOPE_*} 映射。
 * <p>
 * 通过 {@code @ConditionalOnMissingBean} 让使用方自带的 {@code JwtAuthenticationConverter}
 * 优先生效；默认实现与 starter 的映射保持一致，仅额外支持机器客户端 scope。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
public class AiSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${easywing.security.oauth2.jwt.authorities-claim-name:roles}") String authoritiesClaimName,
            @Value("${easywing.security.oauth2.jwt.username-claim-name:preferred_username}") String usernameClaimName) {
        EasyWingJwtAuthoritiesConverter delegate = new EasyWingJwtAuthoritiesConverter(authoritiesClaimName);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(delegate::convert);
        converter.setPrincipalClaimName(usernameClaimName);
        return converter;
    }
}
