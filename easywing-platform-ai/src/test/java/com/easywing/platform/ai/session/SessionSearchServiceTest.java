package com.easywing.platform.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SessionSearchServiceTest {

    @Mock
    private SessionManager sessionManager;

    private SessionSearchService service;

    @BeforeEach
    void setUp() {
        service = new SessionSearchService(sessionManager, new AiProperties());
    }

    private static ConversationSession session(String id, String userId, String tenantId, String name) {
        return ConversationSession.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .name(name)
                .messages(List.of(
                        ChatMessage.builder()
                                .role("user")
                                .content("请解释一下 Java 虚拟线程")
                                .build(),
                        ChatMessage.builder()
                                .role("assistant")
                                .content("虚拟线程是 JDK 21 的轻量级线程。")
                                .build()))
                .lastActiveAt(1000L)
                .build();
    }

    @Test
    void blankQueryReturnsEmpty() {
        assertThat(service.search("u1", "default", "  ", 10).block()).isEmpty();
    }

    @Test
    void matchesMessageContentCaseInsensitive() {
        when(sessionManager.getUserSessions("u1", "default"))
                .thenReturn(Mono.just(List.of(session("s1", "u1", "default", "对话"))));

        List<SessionSearchHit> hits =
                service.search("u1", "default", "java", 10).block();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMessageIndex()).isZero();
        assertThat(hits.get(0).getRole()).isEqualTo("user");
        assertThat(hits.get(0).getSnippet()).contains("Java");
        assertThat(hits.get(0).getSessionId()).isEqualTo("s1");
    }

    @Test
    void matchesSessionName() {
        when(sessionManager.getUserSessions("u1", "default"))
                .thenReturn(Mono.just(List.of(session("s1", "u1", "default", "虚拟线程调研"))));

        List<SessionSearchHit> hits = service.search("u1", "default", "调研", 10).block();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMessageIndex()).isEqualTo(-1);
        assertThat(hits.get(0).getRole()).isEqualTo("session");
    }

    @Test
    void rejectsOtherUsersSessions() {
        when(sessionManager.getUserSessions("u1", "default"))
                .thenReturn(Mono.just(List.of(session("s1", "u2", "default", "对话"))));
        lenient()
                .when(sessionManager.getUserSessions("u2", "default"))
                .thenReturn(Mono.just(List.of(session("s1", "u2", "default", "对话"))));

        assertThat(service.search("u1", "default", "虚拟线程", 10).block()).isEmpty();
    }

    @Test
    void rejectsOtherTenants() {
        when(sessionManager.getUserSessions("u1", "tenant-b"))
                .thenReturn(Mono.just(List.of(session("s1", "u1", "tenant-a", "对话"))));

        assertThat(service.search("u1", "tenant-b", "虚拟线程", 10).block()).isEmpty();
    }

    @Test
    void limitCapsResults() {
        ConversationSession session = session("s1", "u1", "default", "命中");
        when(sessionManager.getUserSessions("u1", "default")).thenReturn(Mono.just(List.of(session)));

        assertThat(service.search("u1", "default", "线程", 1).block()).hasSize(1);
    }

    @Test
    void snippetTruncatesLongContent() {
        String longContent = "A".repeat(500) + "关键字" + "B".repeat(500);
        ConversationSession session = ConversationSession.builder()
                .id("s1")
                .userId("u1")
                .name("n")
                .messages(List.of(
                        ChatMessage.builder().role("user").content(longContent).build()))
                .build();
        when(sessionManager.getUserSessions("u1", "default")).thenReturn(Mono.just(List.of(session)));

        String snippet =
                service.search("u1", "default", "关键字", 10).block().get(0).getSnippet();

        assertThat(snippet.length()).isLessThanOrEqualTo(160);
        assertThat(snippet).contains("关键字");
    }
}
