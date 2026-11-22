package com.easywing.platform.ai.agentdef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link AgentDefinitionService} 单元测试：CRUD、归属/租户隔离、共享与校验。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class AgentDefinitionServiceTest {

    private AgentDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new AgentDefinitionService(new InMemoryAgentDefinitionStore(), new AiProperties());
    }

    private static AgentCaller caller(String userId, String tenant) {
        return new AgentCaller(userId, tenant, List.of());
    }

    private static AgentDefinitionRequest request(String name) {
        AgentDefinitionRequest request = new AgentDefinitionRequest();
        request.setName(name);
        request.setSystemPrompt("你是助手");
        request.setDescription("desc");
        request.setModel("gpt-4o");
        request.setTools(List.of("calculator"));
        request.setSkills(List.of("data-analysis"));
        request.setVisibility("PRIVATE");
        return request;
    }

    @Test
    @DisplayName("创建后可按 id 读取，并记录所有者与租户")
    void createsAndReads() {
        AgentDefinition created =
                service.create(caller("u1", "default"), request("客服助手")).block();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getOwnerId()).isEqualTo("u1");
        assertThat(created.getTenantId()).isEqualTo("default");
        assertThat(created.getVisibility()).isEqualTo("PRIVATE");

        AgentDefinition loaded =
                service.get(caller("u1", "default"), created.getId()).block();
        assertThat(loaded.getName()).isEqualTo("客服助手");
        assertThat(loaded.getTools()).containsExactly("calculator");
        assertThat(loaded.getSkills()).containsExactly("data-analysis");
    }

    @Test
    @DisplayName("创建时字段校验：空名称、空系统提示词、超长与非法可见性被拒绝")
    void rejectsInvalidCreate() {
        AgentDefinitionRequest blankName = request(" ");
        assertThatThrownBy(
                        () -> service.create(caller("u1", "default"), blankName).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        AgentDefinitionRequest blankPrompt = request("名称");
        blankPrompt.setSystemPrompt(" ");
        assertThatThrownBy(() ->
                        service.create(caller("u1", "default"), blankPrompt).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        AgentDefinitionRequest tooLong = request("x".repeat(129));
        assertThatThrownBy(
                        () -> service.create(caller("u1", "default"), tooLong).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        AgentDefinitionRequest badVisibility = request("名称");
        badVisibility.setVisibility("UNKNOWN");
        assertThatThrownBy(() ->
                        service.create(caller("u1", "default"), badVisibility).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("跨用户访问他人定义返回 404（不泄露存在性）")
    void deniesCrossUserAccess() {
        AgentDefinition created =
                service.create(caller("u1", "default"), request("私有助手")).block();

        assertThatThrownBy(() ->
                        service.get(caller("u2", "default"), created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        assertThatThrownBy(() ->
                        service.delete(caller("u2", "default"), created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> service.update(caller("u2", "default"), created.getId(), request("改名"))
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("跨租户访问返回 404")
    void deniesCrossTenantAccess() {
        AgentDefinition created =
                service.create(caller("u1", "tenant-a"), request("租户助手")).block();

        assertThatThrownBy(() ->
                        service.get(caller("u1", "tenant-b"), created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("共享给指定用户后可读取，取消共享后恢复拒绝")
    void sharesWithUser() {
        AgentDefinition created =
                service.create(caller("u1", "default"), request("共享助手")).block();

        AgentDefinition shared = service.share(
                        caller("u1", "default"), created.getId(), shareRequest(List.of("u2"), null))
                .block();
        assertThat(shared.getSharedWithUsers()).containsExactly("u2");
        assertThat(shared.getVisibility()).isEqualTo("USER");
        assertThat(service.get(caller("u2", "default"), created.getId()).block().getId())
                .isEqualTo(created.getId());

        AgentDefinition unshared = service.unshare(
                        caller("u1", "default"), created.getId(), shareRequest(List.of("u2"), null))
                .block();
        assertThat(unshared.getSharedWithUsers()).isEmpty();
        assertThat(unshared.getVisibility()).isEqualTo("PRIVATE");
        assertThatThrownBy(() ->
                        service.get(caller("u2", "default"), created.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("共享给群组后，群成员可读；非群成员不可读")
    void sharesWithGroup() {
        AgentDefinition created =
                service.create(caller("owner", "default"), request("群共享助手")).block();

        AgentDefinition shared = service.share(
                        caller("owner", "default"), created.getId(), shareRequest(null, List.of("admins")))
                .block();
        assertThat(shared.getSharedWithGroups()).containsExactly("admins");

        assertThat(service.get(new AgentCaller("member", "default", List.of("admins")), created.getId())
                        .block()
                        .getId())
                .isEqualTo(created.getId());
        assertThatThrownBy(() -> service.get(new AgentCaller("outsider", "default", List.of("users")), created.getId())
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUBLIC 可见性对同租户所有人可读")
    void publicVisibilityIsReadableWithinTenant() {
        AgentDefinitionRequest publicRequest = request("公开助手");
        publicRequest.setVisibility("PUBLIC");
        AgentDefinition created =
                service.create(caller("u1", "default"), publicRequest).block();

        assertThat(service.get(caller("someone-else", "default"), created.getId())
                        .block()
                        .getId())
                .isEqualTo(created.getId());
        assertThatThrownBy(() -> service.get(caller("someone-else", "other"), created.getId())
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("列举仅返回可访问的定义，按更新时间倒序")
    void listsOnlyReadable() {
        AgentDefinition first =
                service.create(caller("u1", "default"), request("第一个")).block();
        AgentDefinition second =
                service.create(caller("u1", "default"), request("第二个")).block();

        List<AgentDefinition> visible = service.list(caller("u1", "default")).block();
        assertThat(visible).extracting(AgentDefinition::getName).containsExactly("第二个", "第一个");

        List<AgentDefinition> otherUser = service.list(caller("u2", "default")).block();
        assertThat(otherUser).isEmpty();
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("机器客户端接口按租户列举与删除，且不影响其他租户")
    void machineClientScopeIsTenantBound() {
        AgentDefinition a =
                service.create(caller("u1", "tenant-a"), request("A")).block();
        AgentDefinition b =
                service.create(caller("u2", "tenant-b"), request("B")).block();

        assertThat(service.listAll("tenant-a").block())
                .extracting(AgentDefinition::getId)
                .containsExactly(a.getId());
        assertThat(service.listAll("tenant-b").block())
                .extracting(AgentDefinition::getId)
                .containsExactly(b.getId());

        service.deleteAny("tenant-a", a.getId()).block();
        assertThatThrownBy(() -> service.deleteAny("tenant-a", b.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("共享数量上限被拒绝")
    void enforcesShareLimit() {
        AgentDefinition created =
                service.create(caller("u1", "default"), request("限量助手")).block();
        List<String> tooMany = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "user" + i)
                .toList();

        assertThatThrownBy(() -> service.share(caller("u1", "default"), created.getId(), shareRequest(tooMany, null))
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    private static AgentDefinitionShareRequest shareRequest(List<String> users, List<String> groups) {
        AgentDefinitionShareRequest request = new AgentDefinitionShareRequest();
        request.setSharedWithUsers(users);
        request.setSharedWithGroups(groups);
        return request;
    }
}
