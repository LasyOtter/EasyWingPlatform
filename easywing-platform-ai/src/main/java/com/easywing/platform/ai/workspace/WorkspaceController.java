package com.easywing.platform.ai.workspace;

import com.easywing.platform.ai.agentdef.AgentCaller;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Attached Workspaces REST 接口（PRD 4.2 实验性能力）。
 * <p>
 * 整个控制器由 {@code easywing.ai.workspace.enabled=true} 控制注册，默认不暴露任何端点。
 * 用户端点按 JWT 用户 + 租户做归属校验；{@code /admin/**} 仅接受带
 * {@code scope=workspace-management} 的 deployment-bound 机器客户端令牌。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ai/workspaces")
@ApiVersion("v1")
@ConditionalOnProperty(prefix = "easywing.ai.workspace", name = "enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Attached Workspaces", description = "工作区管理接口（PRD 4.2 实验性能力，默认关闭）")
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "列举工作区", description = "返回当前用户 + 租户下的工作区")
    public Mono<ResponseEntity<List<Workspace>>> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(caller(jwt)).map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    @Operation(summary = "读取工作区")
    public Mono<ResponseEntity<Workspace>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.get(caller(jwt), id).map(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(summary = "创建工作区")
    public Mono<ResponseEntity<Workspace>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkspaceRequest request) {
        return service.create(caller(jwt), request)
                .map(workspace -> ResponseEntity.status(HttpStatus.CREATED).body(workspace));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新工作区")
    public Mono<ResponseEntity<Workspace>> update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody WorkspaceRequest request) {
        return service.update(caller(jwt), id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作区", description = "同时删除该工作区目录下的全部文件")
    public Mono<ResponseEntity<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.delete(caller(jwt), id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @GetMapping("/{id}/tree")
    @Operation(summary = "查看目录树", description = "相对路径为空时返回工作区根目录；depth 会被限制在配置上限内")
    public Mono<ResponseEntity<List<WorkspaceTreeNode>>> tree(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer depth) {
        return service.tree(caller(jwt), id, path, depth).map(ResponseEntity::ok);
    }

    @GetMapping("/{id}/files")
    @Operation(summary = "读取工作区文件", description = "UTF-8 文本；路径必须位于工作区根目录内")
    public Mono<ResponseEntity<String>> readFile(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestParam String path) {
        return service.readFile(caller(jwt), id, path).map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/files")
    @Operation(summary = "写入工作区文件")
    public Mono<ResponseEntity<Void>> writeFile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestParam String path,
            @Valid @RequestBody WorkspaceFileWriteRequest request) {
        return service.writeFile(caller(jwt), id, path, request)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @DeleteMapping("/{id}/files")
    @Operation(summary = "删除工作区文件或目录", description = "不允许删除工作区根目录")
    public Mono<ResponseEntity<Void>> deleteFile(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestParam String path) {
        return service.deleteFile(caller(jwt), id, path)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PostMapping("/{id}/bash")
    @Operation(summary = "在工作区执行 Bash", description = "需 allowBash=true 且调用方持有 workspace-management 权限")
    public Mono<ResponseEntity<WorkspaceBashResult>> bash(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody WorkspaceBashRequest request) {
        return service.bash(caller(jwt), id, request).map(ResponseEntity::ok);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('SCOPE_workspace-management')")
    @Operation(summary = "列举租户内全部工作区（机器客户端）", description = "需 deployment-bound 机器客户端令牌，scope=workspace-management")
    public Mono<ResponseEntity<List<Workspace>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        return service.listAll(tenantId(jwt)).map(ResponseEntity::ok);
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('SCOPE_workspace-management')")
    @Operation(summary = "删除租户内任意工作区（机器客户端）", description = "需 deployment-bound 机器客户端令牌，scope=workspace-management")
    public Mono<ResponseEntity<Void>> deleteAny(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.deleteAny(tenantId(jwt), id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    private static AgentCaller caller(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少用户身份");
        }
        return new AgentCaller(jwt.getSubject(), tenantId(jwt), authorities(jwt));
    }

    private static String tenantId(Jwt jwt) {
        Object tenant = jwt == null ? null : jwt.getClaim("tenant_id");
        if (tenant == null && jwt != null) {
            tenant = jwt.getClaim("tenantId");
        }
        return tenant == null ? SessionOwnership.DEFAULT_TENANT : tenant.toString();
    }

    private static List<String> authorities(Jwt jwt) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(claimAsList(jwt, "groups"));
        result.addAll(claimAsList(jwt, "roles"));
        result.addAll(claimAsList(jwt, "scope"));
        result.addAll(claimAsList(jwt, "scp"));
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
