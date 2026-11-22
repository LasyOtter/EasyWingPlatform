package com.easywing.platform.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SessionExportServiceTest {

    @Mock
    private SessionManager sessionManager;

    private SessionExportService service;

    @BeforeEach
    void setUp() {
        service = new SessionExportService(sessionManager);
    }

    private static ConversationSession session(String id, String userId, String tenantId) {
        return ConversationSession.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .name("季度复盘")
                .model("gpt-4o")
                .provider("openai")
                .createdAt(1_700_000_000_000L)
                .updatedAt(1_700_000_001_000L)
                .messageCount(2)
                .messages(List.of(
                        ChatMessage.builder().role("user").content("帮我总结季度目标").build(),
                        ChatMessage.builder()
                                .role("assistant")
                                .content("好的，以下是总结。")
                                .build()))
                .build();
    }

    @Test
    void exportsJson() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(java.util.Optional.of(session("s1", "u1", "default"))));

        SessionExportService.ExportedSession result =
                service.export("s1", "u1", "default", "json").block();

        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.fileName()).isEqualTo("季度复盘.json");
        assertThat(result.body()).contains("季度目标").contains("\"role\" : \"user\"");
    }

    @Test
    void exportsMarkdown() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(java.util.Optional.of(session("s1", "u1", "default"))));

        SessionExportService.ExportedSession result =
                service.export("s1", "u1", "default", "markdown").block();

        assertThat(result.contentType()).contains("text/markdown");
        assertThat(result.fileName()).endsWith(".md");
        assertThat(result.body()).contains("# 季度复盘").contains("## user").contains("## assistant");
    }

    @Test
    void rejectsUnsupportedFormat() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(java.util.Optional.of(session("s1", "u1", "default"))));

        assertThatThrownBy(() -> service.export("s1", "u1", "default", "pdf").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void returns404WhenSessionNotOwned() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(java.util.Optional.of(session("s1", "u2", "default"))));

        assertThatThrownBy(() -> service.export("s1", "u1", "default", "json").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenSessionMissing() {
        when(sessionManager.getSession("missing")).thenReturn(Mono.just(java.util.Optional.empty()));

        assertThatThrownBy(
                        () -> service.export("missing", "u1", "default", "json").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }
}
