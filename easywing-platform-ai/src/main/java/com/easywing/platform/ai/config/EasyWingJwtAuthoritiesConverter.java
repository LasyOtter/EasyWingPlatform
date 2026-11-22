package com.easywing.platform.ai.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * 把 JWT 的 authorities claim 与 scope/scp claim 同时映射为权限。
 * <p>
 * 既有 {@code roles} claim 映射保持不变；机器客户端令牌的 {@code scope} 额外映射为
 * {@code SCOPE_<scope>}，供 {@code @PreAuthorize("hasAuthority('SCOPE_agent-management')")} 使用。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class EasyWingJwtAuthoritiesConverter {

    private final String authoritiesClaimName;

    public EasyWingJwtAuthoritiesConverter(String authoritiesClaimName) {
        this.authoritiesClaimName = authoritiesClaimName;
    }

    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : claimAsList(jwt, authoritiesClaimName)) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        for (String scope : claimAsList(jwt, "scope")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        for (String scope : claimAsList(jwt, "scp")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return new ArrayList<>(authorities);
    }

    private static List<String> claimAsList(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addAll(result, item == null ? null : item.toString());
            }
        } else {
            addAll(result, value.toString());
        }
        return result;
    }

    private static void addAll(List<String> target, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String part : value.split("[,\\s]+")) {
            if (StringUtils.hasText(part)) {
                target.add(part.trim());
            }
        }
    }
}
