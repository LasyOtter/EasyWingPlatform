package com.easywing.platform.ai.agentdef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Agent 定义的调用方上下文：用户 ID、租户、所属群组。
 *
 * @param userId   用户 ID（JWT subject）
 * @param tenantId 租户 ID（未指定时为 {@code default}）
 * @param groups   所属群组/角色（用于按群组共享）
 * @author EasyWing Team
 * @since 1.0.0
 */
public record AgentCaller(String userId, String tenantId, List<String> groups) {

    /**
     * 从已校验的 JWT 构造调用方上下文；令牌缺失主体时返回 {@code null}。
     */
    public static AgentCaller fromJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            return null;
        }
        String tenant = jwt.getClaimAsString("tenant_id");
        Set<String> groups = new LinkedHashSet<>();
        groups.addAll(claimAsList(jwt, "groups"));
        groups.addAll(claimAsList(jwt, "roles"));
        return new AgentCaller(
                jwt.getSubject(), tenant == null || tenant.isBlank() ? "default" : tenant, new ArrayList<>(groups));
    }

    private static List<String> claimAsList(Jwt jwt, String claim) {
        if (jwt == null) {
            return List.of();
        }
        Object value = jwt.getClaim(claim);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                appendToken(result, item == null ? null : item.toString());
            }
        } else {
            appendToken(result, value.toString());
        }
        return result;
    }

    private static void appendToken(List<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }
}
