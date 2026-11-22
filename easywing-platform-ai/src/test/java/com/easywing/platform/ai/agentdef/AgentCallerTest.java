package com.easywing.platform.ai.agentdef;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link AgentCaller#fromJwt(Jwt)} 单元测试：WebSocket 与 REST 共用的身份解析。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class AgentCallerTest {

    private static Jwt jwt(String subject, String tenant, Object groups, Object roles) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256");
        if (subject != null) {
            builder.subject(subject);
        }
        if (tenant != null) {
            builder.claim("tenant_id", tenant);
        }
        if (groups != null) {
            builder.claim("groups", groups);
        }
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    @Test
    @DisplayName("令牌为空返回 null")
    void shouldReturnNullForNullJwt() {
        assertThat(AgentCaller.fromJwt(null)).isNull();
    }

    @Test
    @DisplayName("缺少 subject 返回 null，避免伪造身份")
    void shouldReturnNullWithoutSubject() {
        assertThat(AgentCaller.fromJwt(jwt(null, "tenant-a", List.of(), List.of())))
                .isNull();
    }

    @Test
    @DisplayName("未声明租户时归一为 default")
    void shouldDefaultTenant() {
        AgentCaller caller = AgentCaller.fromJwt(jwt("u1", null, List.of(), List.of()));
        assertThat(caller.userId()).isEqualTo("u1");
        assertThat(caller.tenantId()).isEqualTo("default");
    }

    @Test
    @DisplayName("合并 groups 与 roles 并去重、过滤空白")
    void shouldMergeGroupsAndRoles() {
        AgentCaller caller = AgentCaller.fromJwt(
                jwt("u1", "tenant-a", Arrays.asList("g1", " g2 ", "", null), List.of("g2", "admin")));
        assertThat(caller.tenantId()).isEqualTo("tenant-a");
        assertThat(caller.groups()).containsExactly("g1", "g2", "admin");
    }

    @Test
    @DisplayName("标量声明按单元素处理")
    void shouldHandleScalarClaim() {
        AgentCaller caller = AgentCaller.fromJwt(jwt("u1", "tenant-a", "g1", "admin"));
        assertThat(caller.groups()).containsExactly("g1", "admin");
    }
}
