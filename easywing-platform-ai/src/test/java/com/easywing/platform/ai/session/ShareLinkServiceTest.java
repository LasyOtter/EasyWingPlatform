package com.easywing.platform.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private SessionManager sessionManager;

    private ShareLinkService service;

    private ShareLinkProperties properties;

    private InMemoryShareLinkStore shareStore;

    @BeforeEach
    void setUp() {
        properties = new ShareLinkProperties();
        properties.setEnabled(true);
        properties.setRateLimitPerMinute(30);
        shareStore = new InMemoryShareLinkStore();
        service = new ShareLinkService(sessionManager, shareStore, properties);
    }

    private static ConversationSession session(String id, String userId, String tenantId) {
        return ConversationSession.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .name("共享会话")
                .model("gpt-4o")
                .provider("openai")
                .createdAt(1_700_000_000_000L)
                .messageCount(2)
                .messages(List.of(
                        ChatMessage.builder().role("user").content("你好").build(),
                        ChatMessage.builder()
                                .role("assistant")
                                .content("你好，我是助手")
                                .build()))
                .build();
    }

    private void givenOwnedSession(String id, String userId, String tenantId) {
        when(sessionManager.getSession(id)).thenReturn(Mono.just(Optional.of(session(id, userId, tenantId))));
    }

    @Test
    void createsShareAndReadsSharedView() {
        givenOwnedSession("s1", "u1", "default");

        ShareLink link = service.create("s1", "u1", "default", 24, 0).block();

        assertThat(link).isNotNull();
        assertThat(link.getId()).isNotBlank();
        assertThat(link.getExpiresAt()).isGreaterThan(link.getCreatedAt());
        assertThat(link.getAccessCount()).isZero();

        ShareLinkService.SharedSessionView view =
                service.getShared(link.getId()).block();
        assertThat(view).isNotNull();
        assertThat(view.token()).isEqualTo(link.getId());
        assertThat(view.sessionName()).isEqualTo("共享会话");
        assertThat(view.messages()).hasSize(2);
        assertThat(view.messages().get(0).content()).isEqualTo("你好");
        assertThat(shareStore.findById(link.getId()).getAccessCount()).isEqualTo(1);

        assertThat(service.getShared(link.getId()).block()).isNotNull();
        assertThat(shareStore.findById(link.getId()).getAccessCount()).isEqualTo(2);
    }

    @Test
    void rejectsWhenDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.create("s1", "u1", "default", 1, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsTtlAboveMax() {
        properties.setMaxTtlHours(10);

        assertThatThrownBy(() -> service.create("s1", "u1", "default", 11, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsNegativeTtl() {
        assertThatThrownBy(() -> service.create("s1", "u1", "default", -1, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforcesMaxLinksPerSession() {
        givenOwnedSession("s1", "u1", "default");
        properties.setMaxLinksPerSession(2);

        service.create("s1", "u1", "default", 1, 0).block();
        service.create("s1", "u1", "default", 1, 0).block();

        assertThatThrownBy(() -> service.create("s1", "u1", "default", 1, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void returns404WhenCreatingForForeignSession() {
        givenOwnedSession("s1", "u2", "default");

        assertThatThrownBy(() -> service.create("s1", "u1", "default", 1, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenCreatingAcrossTenants() {
        givenOwnedSession("s1", "u1", "tenant-a");

        assertThatThrownBy(() -> service.create("s1", "u1", "tenant-b", 1, 0).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void revokedLinkBecomesInvisible() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        service.revoke(link.getId(), "u1", "default").block();

        assertThatThrownBy(() -> service.getShared(link.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredLinkBecomesInvisible() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        ShareLink stored = shareStore.findById(link.getId());
        stored.setExpiresAt(1L);
        shareStore.save(stored);

        assertThatThrownBy(() -> service.getShared(link.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void accessLimitExhaustionBecomesInvisible() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 1).block();

        assertThat(service.getShared(link.getId()).block()).isNotNull();

        assertThatThrownBy(() -> service.getShared(link.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void rateLimitReturns429() {
        givenOwnedSession("s1", "u1", "default");
        properties.setRateLimitPerMinute(2);
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        assertThat(service.getShared(link.getId()).block()).isNotNull();
        assertThat(service.getShared(link.getId()).block()).isNotNull();

        assertThatThrownBy(() -> service.getShared(link.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void revokeByNonOwnerReturns404() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        assertThatThrownBy(() -> service.revoke(link.getId(), "u2", "default").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void listSharesOnlyReturnsOwnedSessionLinks() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        List<ShareLink> links = service.listBySession("s1", "u1", "default").block();

        assertThat(links).extracting(ShareLink::getId).containsExactly(link.getId());
    }

    @Test
    void sharedViewNeverExposesOwnerIdentity() {
        givenOwnedSession("s1", "u1", "default");
        ShareLink link = service.create("s1", "u1", "default", 1, 0).block();

        ShareLinkService.SharedSessionView view =
                service.getShared(link.getId()).block();

        assertThat(view.toString()).doesNotContain("u1");
    }
}
