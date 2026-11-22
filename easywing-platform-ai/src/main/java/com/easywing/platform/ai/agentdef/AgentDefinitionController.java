package com.easywing.platform.ai.agentdef;

import com.easywing.platform.ai.gateway.ApiVersion;
import com.easywing.platform.ai.session.SessionOwnership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Agent 定义管理 REST 接口（PRD 4.2）。
 * <p>
 * 用户端点按 JWT 用户 + 租户做归属校验；{@code /admin/**} 仅接受带
 * {@code scope=agent-management} 的 deployment-bound 机器客户端令牌。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ai/agents")
@ApiVersion("v1")
@ConditionalOnProperty(prefix = "easywing.ai.agent-def", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Agent Definitions", description = "Agent 定义管理接口（PRD 4.2）")
public class AgentDefinitionController {

    private final AgentDefinitionService service;

    public AgentDefinitionController(AgentDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "列举 Agent 定义", description = "返回调用者可访问的定义：本人创建 + 被共享 + 租户内公开")
    public Mono<ResponseEntity<List<AgentDefinition>>> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(caller(jwt)).map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    @Operation(summary = "读取 Agent 定义")
    public Mono<ResponseEntity<AgentDefinition>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.get(caller(jwt), id).map(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(summary = "创建 Agent 定义")
    public Mono<ResponseEntity<AgentDefinition>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgentDefinitionRequest request) {
        return service.create(caller(jwt), request)
                .map(definition -> ResponseEntity.status(HttpStatus.CREATED).body(definition));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent 定义")
    public Mono<ResponseEntity<AgentDefinition>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody AgentDefinitionRequest request) {
        return service.update(caller(jwt), id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent 定义")
    public Mono<ResponseEntity<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.delete(caller(jwt), id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "共享 Agent 定义", description = "向指定用户/群组追加共享；可同步调整可见性")
    public Mono<ResponseEntity<AgentDefinition>> share(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody AgentDefinitionShareRequest request) {
        return service.share(caller(jwt), id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}/share")
    @Operation(summary = "取消共享 Agent 定义", description = "从共享名单移除；名单清空后可见性回落为 PRIVATE")
    public Mono<ResponseEntity<AgentDefinition>> unshare(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody AgentDefinitionShareRequest request) {
        return service.unshare(caller(jwt), id, request).map(ResponseEntity::ok);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('SCOPE_agent-management')")
    @Operation(summary = "列举租户内全部 Agent 定义（机器客户端）", description = "需 deployment-bound 机器客户端令牌，scope=agent-management")
    public Mono<ResponseEntity<List<AgentDefinition>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        return service.listAll(tenantId(jwt)).map(ResponseEntity::ok);
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('SCOPE_agent-management')")
    @Operation(summary = "删除租户内任意 Agent 定义（机器客户端）", description = "需 deployment-bound 机器客户端令牌，scope=agent-management")
    public Mono<ResponseEntity<Void>> deleteAny(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.deleteAny(tenantId(jwt), id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    private static AgentCaller caller(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少用户身份");
        }
        return new AgentCaller(jwt.getSubject(), tenantId(jwt), groups(jwt));
    }

    private static String tenantId(Jwt jwt) {
        Object tenant = jwt == null ? null : jwt.getClaim("tenant_id");
        if (tenant == null && jwt != null) {
            tenant = jwt.getClaim("tenantId");
        }
        return tenant == null ? SessionOwnership.DEFAULT_TENANT : tenant.toString();
    }

    private static List<String> groups(Jwt jwt) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(claimAsList(jwt, "groups"));
        result.addAll(claimAsList(jwt, "roles"));
        return new ArrayList<>(result);
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
