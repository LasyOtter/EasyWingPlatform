package com.easywing.platform.oauth2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 资源服务器配置属性
 * <p>
 * 对应配置前缀 {@code easywing.security.oauth2.jwt}。
 * 这些值必须与认证服务签发令牌时使用的算法和 claim 名保持一致。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.security.oauth2.jwt")
public class OAuth2Properties {

    /**
     * JWK Set 地址，供资源服务器在线验证 JWT 签名
     */
    private String jwkSetUri;

    /**
     * OIDC issuer 地址，可自动发现 jwk-set-uri（与 jwkSetUri 二选一）
     */
    private String issuerUri;

    /**
     * 权限 claim 名，需与认证服务 {@code AuthProperties.Jwt.rolesClaimName} 一致
     */
    private String authoritiesClaimName = "roles";

    /**
     * 用户名 claim 名
     */
    private String usernameClaimName = "preferred_username";
}
