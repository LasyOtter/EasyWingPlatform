package com.easywing.platform.ai.workspace;

import com.easywing.platform.ai.agentdef.AgentCaller;
import com.easywing.platform.ai.session.SessionOwnership;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Attached Workspaces 服务（PRD 4.2 实验性能力，默认关闭）。
 * <p>
 * 工作区元数据按「用户 + 租户」归属，所有文件操作前先做归属校验；跨用户或跨租户访问统一返回
 * 404，避免探测资源是否存在。文件路径经归一化后必须落在工作区根目录内，拒绝目录穿越；
 * 单文件大小、文件数量、目录深度均有上限。Bash 执行额外要求 {@code allowBash=true}
 * 且调用方持有 {@code workspace-management} 权限。
 * <p>
 * 注意：Bash 走宿主进程（{@code cmd /c} 或 {@code sh -c}），仅做超时与输出截断，
 * <b>不是容器级隔离</b>；生产环境应把工作区放到独立容器/用户中执行。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class WorkspaceService {

    private static final int MAX_COMMAND_LENGTH = 4096;

    private final WorkspaceStore store;
    private final WorkspaceProperties properties;

    public WorkspaceService(WorkspaceStore store, WorkspaceProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public Mono<Workspace> create(AgentCaller caller, WorkspaceRequest request) {
        return Mono.fromCallable(() -> {
            validateRequest(request);
            if (store.countByOwner(caller.userId(), caller.tenantId()) >= properties.getMaxWorkspacesPerUser()) {
                throw badRequest("工作区数量已达上限 " + properties.getMaxWorkspacesPerUser());
            }
            long now = System.currentTimeMillis();
            Workspace workspace = Workspace.builder()
                    .id(UUID.randomUUID().toString())
                    .name(request.getName().trim())
                    .description(trimToNull(request.getDescription()))
                    .type(WorkspaceType.from(request.getType()).name())
                    .ownerId(caller.userId())
                    .tenantId(SessionOwnership.normalizeTenant(caller.tenantId()))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            store.save(workspace);
            try {
                Files.createDirectories(workspaceRoot(workspace.getId()));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建工作区目录失败");
            }
            return workspace;
        });
    }

    public Mono<Workspace> update(AgentCaller caller, String id, WorkspaceRequest request) {
        return Mono.fromCallable(() -> {
            Workspace existing = requireOwned(id, caller);
            validateRequest(request);
            existing.setName(request.getName().trim());
            existing.setDescription(trimToNull(request.getDescription()));
            existing.setType(WorkspaceType.from(request.getType()).name());
            existing.setUpdatedAt(System.currentTimeMillis());
            store.save(existing);
            return existing;
        });
    }

    public Mono<Workspace> get(AgentCaller caller, String id) {
        return Mono.fromCallable(() -> requireOwned(id, caller));
    }

    public Mono<List<Workspace>> list(AgentCaller caller) {
        return Mono.fromCallable(
                () -> store.listByOwner(caller.userId(), SessionOwnership.normalizeTenant(caller.tenantId())));
    }

    public Mono<Void> delete(AgentCaller caller, String id) {
        return Mono.fromRunnable(() -> {
            requireOwned(id, caller);
            store.delete(id);
            deleteRecursively(workspaceRoot(id));
        });
    }

    public Mono<List<WorkspaceTreeNode>> tree(AgentCaller caller, String id, String path, Integer depth) {
        return Mono.fromCallable(() -> {
            requireOwned(id, caller);
            Path root = workspaceRoot(id);
            Path target = resolveSafe(id, path);
            if (!Files.exists(target)) {
                throw notFound();
            }
            int maxDepth = depth == null || depth <= 0
                    ? properties.getMaxTreeDepth()
                    : Math.min(depth, properties.getMaxTreeDepth());
            if (Files.isRegularFile(target)) {
                return List.of(nodeFor(target, root));
            }
            return List.of(buildNode(target, root, 0, maxDepth));
        });
    }

    public Mono<String> readFile(AgentCaller caller, String id, String path) {
        return Mono.fromCallable(() -> {
            requireOwned(id, caller);
            Path file = resolveSafe(id, path);
            if (!Files.isRegularFile(file)) {
                throw notFound();
            }
            try {
                if (Files.size(file) > properties.getMaxFileSize()) {
                    throw badRequest("文件超过大小上限 " + properties.getMaxFileSize() + " 字节");
                }
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件失败");
            }
        });
    }

    public Mono<Void> writeFile(AgentCaller caller, String id, String path, WorkspaceFileWriteRequest request) {
        return Mono.fromRunnable(() -> {
            requireOwned(id, caller);
            if (request == null || request.getContent() == null) {
                throw badRequest("文件内容不能为空");
            }
            byte[] bytes = request.getContent().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > properties.getMaxFileSize()) {
                throw badRequest("文件内容超过大小上限 " + properties.getMaxFileSize() + " 字节");
            }
            Path file = resolveSafe(id, path);
            try {
                if (Files.isDirectory(file)) {
                    throw badRequest("目标路径是目录");
                }
                if (!Files.exists(file) && countFiles(workspaceRoot(id)) >= properties.getMaxFilesPerWorkspace()) {
                    throw badRequest("工作区文件数量已达上限 " + properties.getMaxFilesPerWorkspace());
                }
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.write(file, bytes);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入文件失败");
            }
        });
    }

    public Mono<Void> deleteFile(AgentCaller caller, String id, String path) {
        return Mono.fromRunnable(() -> {
            requireOwned(id, caller);
            Path root = workspaceRoot(id);
            Path target = resolveSafe(id, path);
            if (target.equals(root)) {
                throw badRequest("不能删除工作区根目录");
            }
            try {
                if (Files.isDirectory(target)) {
                    deleteRecursively(target);
                } else if (Files.exists(target)) {
                    Files.delete(target);
                } else {
                    throw notFound();
                }
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败");
            }
        });
    }

    /**
     * 在工作区根目录执行 Bash。要求 {@code allowBash=true} 且调用方持有管理权限。
     */
    public Mono<WorkspaceBashResult> bash(AgentCaller caller, String id, WorkspaceBashRequest request) {
        return Mono.fromCallable(() -> {
            requireOwned(id, caller);
            if (!properties.isAllowBash()) {
                throw badRequest("工作区 Bash 执行未启用");
            }
            if (!hasAdminScope(caller)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少工作区管理权限");
            }
            String command = request == null ? null : request.getCommand();
            if (!StringUtils.hasText(command)) {
                throw badRequest("命令不能为空");
            }
            if (command.length() > MAX_COMMAND_LENGTH) {
                throw badRequest("命令长度不能超过 " + MAX_COMMAND_LENGTH);
            }
            return runBash(workspaceRoot(id), command);
        });
    }

    /** 机器客户端专用：列举租户内全部工作区。 */
    public Mono<List<Workspace>> listAll(String tenantId) {
        String tenant = SessionOwnership.normalizeTenant(tenantId);
        return Mono.fromCallable(() -> store.listAll().stream()
                .filter(workspace -> SessionOwnership.normalizeTenant(workspace.getTenantId())
                        .equals(tenant))
                .sorted(Comparator.comparingLong(Workspace::getUpdatedAt).reversed())
                .toList());
    }

    /** 机器客户端专用：删除租户内任意工作区。 */
    public Mono<Void> deleteAny(String tenantId, String id) {
        String tenant = SessionOwnership.normalizeTenant(tenantId);
        return Mono.fromRunnable(() -> {
            Workspace workspace = store.findById(id);
            if (workspace == null
                    || !SessionOwnership.normalizeTenant(workspace.getTenantId())
                            .equals(tenant)) {
                throw notFound();
            }
            store.delete(id);
            deleteRecursively(workspaceRoot(id));
        });
    }

    private Workspace requireOwned(String id, AgentCaller caller) {
        Workspace workspace = store.findById(id);
        if (workspace == null
                || workspace.getOwnerId() == null
                || !workspace.getOwnerId().equals(caller.userId())
                || !SessionOwnership.normalizeTenant(workspace.getTenantId())
                        .equals(SessionOwnership.normalizeTenant(caller.tenantId()))) {
            throw notFound();
        }
        return workspace;
    }

    private void validateRequest(WorkspaceRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw badRequest("名称不能为空");
        }
        if (request.getName().length() > properties.getMaxNameLength()) {
            throw badRequest("名称长度不能超过 " + properties.getMaxNameLength());
        }
        if (request.getDescription() != null
                && request.getDescription().length() > properties.getMaxDescriptionLength()) {
            throw badRequest("描述长度不能超过 " + properties.getMaxDescriptionLength());
        }
        if (StringUtils.hasText(request.getType())) {
            try {
                WorkspaceType.parse(request.getType());
            } catch (IllegalArgumentException e) {
                throw badRequest(e.getMessage());
            }
        }
    }

    private Path workspaceRoot(String id) {
        return resolveBaseDir().resolve(id).normalize();
    }

    private Path resolveBaseDir() {
        String configured = properties.getBaseDir();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), "easywing", "workspaces")
                .toAbsolutePath()
                .normalize();
    }

    /** 把相对路径收敛到工作区根目录内，拒绝绝对路径与 {@code ..} 穿越。 */
    private Path resolveSafe(String id, String relative) {
        Path root = workspaceRoot(id);
        if (relative == null || relative.isBlank()) {
            return root;
        }
        if (relative.length() > properties.getMaxPathLength()) {
            throw badRequest("路径长度不能超过 " + properties.getMaxPathLength());
        }
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw badRequest("非法路径");
        }
        return candidate;
    }

    private WorkspaceTreeNode buildNode(Path directory, Path root, int level, int maxDepth) {
        WorkspaceTreeNode node = nodeFor(directory, root);
        if (level >= maxDepth || !Files.isDirectory(directory)) {
            return node;
        }
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> sorted = children.sorted(
                            Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path child : sorted) {
                if (Files.isDirectory(child)) {
                    node.getChildren().add(buildNode(child, root, level + 1, maxDepth));
                } else {
                    node.getChildren().add(nodeFor(child, root));
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取目录失败");
        }
        return node;
    }

    private WorkspaceTreeNode nodeFor(Path path, Path root) {
        boolean directory = Files.isDirectory(path);
        long size = 0L;
        if (!directory) {
            try {
                size = Files.size(path);
            } catch (IOException ignored) {
                // 文件在列举期间被删除时按 0 处理
            }
        }
        String relative = root.relativize(path).toString().replace('\\', '/');
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return WorkspaceTreeNode.builder()
                .name(name)
                .path(relative)
                .type(directory ? "DIRECTORY" : "FILE")
                .size(size)
                .build();
    }

    private long countFiles(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除目录失败");
        }
    }

    private boolean hasAdminScope(AgentCaller caller) {
        String scope = properties.getAdminScope();
        if (!StringUtils.hasText(scope) || caller.groups() == null) {
            return false;
        }
        return caller.groups().contains(scope) || caller.groups().contains("SCOPE_" + scope);
    }

    private WorkspaceBashResult runBash(Path root, String command) {
        ProcessBuilder builder = new ProcessBuilder();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(root.toFile());
        try {
            Files.createDirectories(root);
            Process process = builder.start();
            CompletableFuture<byte[]> stdout = readAsync(process.getInputStream());
            CompletableFuture<byte[]> stderr = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(properties.getBashTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new WorkspaceBashResult(-1, truncate(stdout.join()), truncate(stderr.join()), true);
            }
            return new WorkspaceBashResult(
                    process.exitValue(), truncate(stdout.join()), truncate(stderr.join()), false);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "执行命令失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "执行被中断");
        }
    }

    private static CompletableFuture<byte[]> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream ignored = stream) {
                return stream.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
    }

    private String truncate(byte[] bytes) {
        int limit = Math.max(0, properties.getBashMaxOutputSize());
        if (bytes.length <= limit) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, 0, limit, StandardCharsets.UTF_8) + "\n[Output truncated]";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "工作区不存在");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
