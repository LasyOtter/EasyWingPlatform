package com.easywing.platform.ai.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.agentdef.AgentCaller;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link WorkspaceService} 单元测试：归属隔离、路径安全、上限与 Bash 开关。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class WorkspaceServiceTest {

    private static final AgentCaller ALICE = new AgentCaller("alice", "tenant-a", List.of());
    private static final AgentCaller BOB = new AgentCaller("bob", "tenant-a", List.of());
    private static final AgentCaller ALICE_OTHER_TENANT = new AgentCaller("alice", "tenant-b", List.of());

    @TempDir
    Path tempDir;

    private WorkspaceProperties properties;
    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        properties = new WorkspaceProperties();
        properties.setBaseDir(tempDir.toString());
        service = new WorkspaceService(new InMemoryWorkspaceStore(), properties);
    }

    private WorkspaceRequest request(String name) {
        WorkspaceRequest request = new WorkspaceRequest();
        request.setName(name);
        request.setDescription("desc");
        request.setType("PERSONAL");
        return request;
    }

    @Test
    void createAndGetShouldReturnOwnedWorkspace() {
        Workspace created = service.create(ALICE, request("my-ws")).block();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getOwnerId()).isEqualTo("alice");
        assertThat(created.getTenantId()).isEqualTo("tenant-a");
        assertThat(created.getType()).isEqualTo("PERSONAL");

        Workspace loaded = service.get(ALICE, created.getId()).block();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("my-ws");
    }

    @Test
    void listShouldOnlyReturnCallerOwnWorkspaces() {
        Workspace aliceWs = service.create(ALICE, request("alice-ws")).block();
        service.create(BOB, request("bob-ws")).block();

        List<Workspace> aliceList = service.list(ALICE).block();

        assertThat(aliceList).extracting(Workspace::getId).containsExactly(aliceWs.getId());
    }

    @Test
    void updateShouldModifyOwnedWorkspace() {
        Workspace created = service.create(ALICE, request("before")).block();

        WorkspaceRequest update = request("after");
        update.setType("MANAGED");
        Workspace updated = service.update(ALICE, created.getId(), update).block();

        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("after");
        assertThat(updated.getType()).isEqualTo("MANAGED");
    }

    @Test
    void crossUserAccessShouldBeNotFound() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();

        assertThatThrownBy(() -> service.get(BOB, created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void crossTenantAccessShouldBeNotFound() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();

        assertThatThrownBy(
                        () -> service.get(ALICE_OTHER_TENANT, created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteShouldRemoveMetadataAndFiles() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        service.writeFile(ALICE, created.getId(), "dir/a.txt", content("hello")).block();

        service.delete(ALICE, created.getId()).block();

        assertThatThrownBy(() -> service.get(ALICE, created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        assertThat(tempDir.resolve(created.getId())).doesNotExist();
    }

    @Test
    void writeAndReadFileShouldRoundTrip() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();

        service.writeFile(ALICE, created.getId(), "notes/a.txt", content("round trip"))
                .block();
        String read = service.readFile(ALICE, created.getId(), "notes/a.txt").block();

        assertThat(read).isEqualTo("round trip");
    }

    @Test
    void readFileShouldRejectOversizedContent() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        service.writeFile(ALICE, created.getId(), "big.txt", content("12345")).block();
        properties.setMaxFileSize(4L);

        assertThatThrownBy(() ->
                        service.readFile(ALICE, created.getId(), "big.txt").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void writeFileShouldRejectOversizedContent() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        properties.setMaxFileSize(4L);

        assertThatThrownBy(() -> service.writeFile(ALICE, created.getId(), "big.txt", content("12345"))
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversalShouldBeRejected() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();

        assertThatThrownBy(() -> service.readFile(ALICE, created.getId(), "../outside.txt")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() ->
                        service.readFile(ALICE, created.getId(), "/etc/passwd").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void treeShouldRespectDepthLimit() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        service.writeFile(ALICE, created.getId(), "a/b/c.txt", content("deep")).block();
        properties.setMaxTreeDepth(1);

        List<WorkspaceTreeNode> tree =
                service.tree(ALICE, created.getId(), null, null).block();

        assertThat(tree).hasSize(1);
        WorkspaceTreeNode root = tree.get(0);
        assertThat(root.getType()).isEqualTo("DIRECTORY");
        assertThat(root.getChildren()).hasSize(1);
        WorkspaceTreeNode level1 = root.getChildren().get(0);
        assertThat(level1.getName()).isEqualTo("a");
        assertThat(level1.getChildren()).isEmpty();
    }

    @Test
    void deleteFileShouldRefuseWorkspaceRoot() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();

        assertThatThrownBy(
                        () -> service.deleteFile(ALICE, created.getId(), null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void bashShouldBeRejectedWhenDisabled() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        properties.setAllowBash(false);
        AgentCaller admin = new AgentCaller("alice", "tenant-a", List.of("workspace-management"));

        assertThatThrownBy(() ->
                        service.bash(admin, created.getId(), bash("echo hi")).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void bashShouldBeForbiddenWithoutAdminScope() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        properties.setAllowBash(true);

        assertThatThrownBy(() ->
                        service.bash(ALICE, created.getId(), bash("echo hi")).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void bashShouldRunWithAdminScope() {
        Workspace created = service.create(ALICE, request("alice-ws")).block();
        properties.setAllowBash(true);
        AgentCaller admin = new AgentCaller("alice", "tenant-a", List.of("SCOPE_workspace-management"));

        WorkspaceBashResult result =
                service.bash(admin, created.getId(), bash("echo hello")).block();

        assertThat(result).isNotNull();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("hello");
    }

    @Test
    void createShouldEnforceWorkspaceQuota() {
        properties.setMaxWorkspacesPerUser(1);
        service.create(ALICE, request("first")).block();

        assertThatThrownBy(() -> service.create(ALICE, request("second")).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void createShouldRejectBlankName() {
        assertThatThrownBy(() -> service.create(ALICE, request("  ")).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void createShouldRejectInvalidType() {
        WorkspaceRequest request = request("ws");
        request.setType("NOPE");

        assertThatThrownBy(() -> service.create(ALICE, request).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void machineClientListAllShouldBeTenantScoped() {
        Workspace aliceWs = service.create(ALICE, request("alice-ws")).block();
        service.create(ALICE_OTHER_TENANT, request("other-ws")).block();

        List<Workspace> all = service.listAll("tenant-a").block();

        assertThat(all).extracting(Workspace::getId).containsExactly(aliceWs.getId());
    }

    @Test
    void machineClientDeleteAnyShouldBeTenantScoped() {
        Workspace created =
                service.create(ALICE_OTHER_TENANT, request("other-ws")).block();

        assertThatThrownBy(() -> service.deleteAny("tenant-a", created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        assertThat(service.get(ALICE_OTHER_TENANT, created.getId()).block()).isNotNull();
    }

    private static WorkspaceFileWriteRequest content(String value) {
        WorkspaceFileWriteRequest request = new WorkspaceFileWriteRequest();
        request.setContent(value);
        return request;
    }

    private static WorkspaceBashRequest bash(String command) {
        WorkspaceBashRequest request = new WorkspaceBashRequest();
        request.setCommand(command);
        return request;
    }
}
