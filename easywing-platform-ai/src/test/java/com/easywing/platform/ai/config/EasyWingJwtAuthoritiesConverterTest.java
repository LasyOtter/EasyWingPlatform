package com.easywing.platform.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link EasyWingJwtAuthoritiesConverter} 单元测试：roles 与 scope/scp 的权限映射。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class EasyWingJwtAuthoritiesConverterTest {

    private static Jwt jwt(Object roles, Object scope, Object scp) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("u1")
                .claim("roles", roles)
                .claim("scope", scope)
                .claim("scp", scp)
                .build();
    }

    private static List<String> authorities(Collection<GrantedAuthority> granted) {
        return granted.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("roles 与 scope 同时映射，scope 生成 SCOPE_ 权限")
    void mapsRolesAndScopes() {
        EasyWingJwtAuthoritiesConverter converter = new EasyWingJwtAuthoritiesConverter("roles");

        Collection<GrantedAuthority> authorities =
                converter.convert(jwt(List.of("ADMIN", "USER"), "agent-management read", null));

        assertThat(authorities(authorities))
                .containsExactlyInAnyOrder("ADMIN", "USER", "SCOPE_agent-management", "SCOPE_read");
    }

    @Test
    @DisplayName("scope 为单个字符串时同样映射；scp 作为备用 claim")
    void handlesStringScopeAndScp() {
        EasyWingJwtAuthoritiesConverter converter = new EasyWingJwtAuthoritiesConverter("roles");

        Collection<GrantedAuthority> authorities = converter.convert(jwt("USER", "agent-management", "scp-a"));

        assertThat(authorities(authorities)).containsExactlyInAnyOrder("USER", "SCOPE_agent-management", "SCOPE_scp-a");
    }

    @Test
    @DisplayName("缺失 authorities claim 时返回空权限集合")
    void handlesMissingClaims() {
        EasyWingJwtAuthoritiesConverter converter = new EasyWingJwtAuthoritiesConverter("roles");

        Collection<GrantedAuthority> authorities = converter.convert(jwt(null, null, null));

        assertThat(authorities(authorities)).isEmpty();
    }

    @Test
    @DisplayName("自定义 authorities claim 名生效")
    void supportsCustomClaimName() {
        EasyWingJwtAuthoritiesConverter converter = new EasyWingJwtAuthoritiesConverter("authorities");

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("u1")
                .claim("authorities", List.of("ROLE_ADMIN"))
                .build();

        assertThat(authorities(converter.convert(jwt))).containsExactly("ROLE_ADMIN");
    }
}
