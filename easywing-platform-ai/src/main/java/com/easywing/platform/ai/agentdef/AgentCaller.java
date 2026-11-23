package com.easywing.platform.ai.agentdef;

import com.easywing.platform.ai.session.SessionOwnership;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Agent 定义的调用方上下文：用户 ID、租户、所属群组。
 * <p>
 * 全平台唯一的 JWT 身份解析入口：用户端点、WebSocket 与管理端点都经由此处得到一致的
 * 租户和群组语义，避免同一令牌在不同端点被解析出不同归属。
 *
 * @param userId   用户 ID（JWT subject）
 * @param tenantId 租户 ID（未指定或空白时为 {@code default}）
 * @param groups   所属群组/角色（用于按群组共享；机器客户端含 scope/scp）
 * @author EasyWing Team
 * @since 1.0.0
 */
public record AgentCaller(String userId, String tenantId, List<String> groups) {

    private static final String[] DEFAULT_GROUP_CLAIMS = {"groups", "roles"};

    /**
     * 从已校验的 JWT 构造调用方上下文；令牌缺失主体时返回 {@code null}。
     */
    public static AgentCaller fromJwt(Jwt jwt) {
        return fromJwt(jwt, new String[0]);
    }

    /**
     * 从已校验的 JWT 构造调用方上下文，并额外把指定 claim 并入群组。
     * <p>机器客户端的权限声明在 {@code scope}/{@code scp} 而非 {@code roles}，
     * 需要据此做归属/授权判断的端点应显式传入这两个 claim 名。
     *
     * @param jwt              已校验的 JWT
     * @param additionalClaims 需要并入群组的额外 claim 名
     */
    public static AgentCaller fromJwt(Jwt jwt, String... additionalClaims) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            return null;
        }
        Set<String> groups = new LinkedHashSet<>();
        for (String claim : DEFAULT_GROUP_CLAIMS) {
            groups.addAll(claimAsList(jwt, claim));
        }
        if (additionalClaims != null) {
            for (String claim : additionalClaims) {
                if (StringUtils.hasText(claim)) {
                    groups.addAll(claimAsList(jwt, claim));
                }
            }
        }
        return new AgentCaller(jwt.getSubject(), tenantIdOf(jwt), new ArrayList<>(groups));
    }

    /**
     * 解析租户 ID：依次读取 {@code tenant_id}、{@code tenantId}，空白归一为 {@code default}。
     * <p>不要求令牌带有 subject，管理端点（机器客户端令牌）也可使用。
     */
    public static String tenantIdOf(Jwt jwt) {
        Object tenant = jwt == null ? null : jwt.getClaim("tenant_id");
        if (tenant == null && jwt != null) {
            tenant = jwt.getClaim("tenantId");
        }
        return SessionOwnership.normalizeTenant(tenant == null ? null : tenant.toString());
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
