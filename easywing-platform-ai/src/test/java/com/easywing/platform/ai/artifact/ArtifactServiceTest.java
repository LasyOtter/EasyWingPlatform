package com.easywing.platform.ai.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
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
class ArtifactServiceTest {

    @Mock
    private SessionManager sessionManager;

    private ArtifactService artifactService;

    @BeforeEach
    void setUp() {
        artifactService = new ArtifactService(sessionManager);
        lenient().when(sessionManager.getSession("missing")).thenReturn(Mono.empty());
    }

    private static ConversationSession session(String id, String userId, String tenantId, ChatMessage... messages) {
        return ConversationSession.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .messages(new java.util.ArrayList<>(List.of(messages)))
                .updatedAt(1_700_000_000_000L)
                .build();
    }

    private static ChatMessage message(String role, String content) {
        return ChatMessage.builder().role(role).content(content).build();
    }

    @Test
    void listsArtifactsWithDeterministicIdsAndOwnership() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(Optional.of(session(
                        "s1",
                        "u1",
                        "default",
                        message("assistant", "```html\n<div>a</div>\n```\n```mermaid\ngraph TD; A-->B\n```")))));

        List<Artifact> artifacts =
                artifactService.listArtifacts("s1", "u1", "default").block();

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts.get(0).getId()).isEqualTo("html-0");
        assertThat(artifacts.get(1).getId()).isEqualTo("mermaid-1");
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.getSessionId()).isEqualTo("s1");
            assertThat(artifact.getUserId()).isEqualTo("u1");
            assertThat(artifact.getTenantId()).isEqualTo("default");
        });
    }

    @Test
    void treatsNullTenantAsDefault() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(
                        Optional.of(session("s1", "u1", null, message("assistant", "```html\n<div>a</div>\n```")))));

        assertThat(artifactService.listArtifacts("s1", "u1", "default").block()).hasSize(1);
    }

    @Test
    void returns404WhenSessionMissing() {
        assertThatThrownBy(() -> artifactService
                        .listArtifacts("missing", "u1", "default")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenUserMismatch() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(Optional.of(session("s1", "owner", "default", message("user", "hi")))));

        assertThatThrownBy(() -> artifactService
                        .listArtifacts("s1", "intruder", "default")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenTenantMismatch() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(Optional.of(session("s1", "u1", "tenant-a", message("user", "hi")))));

        assertThatThrownBy(() ->
                        artifactService.listArtifacts("s1", "u1", "tenant-b").block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getArtifactReturnsMatchingArtifact() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(Optional.of(
                        session("s1", "u1", "default", message("assistant", "```mermaid\ngraph TD; A-->B\n```")))));

        Artifact artifact =
                artifactService.getArtifact("s1", "mermaid-0", "u1", "default").block();

        assertThat(artifact).isNotNull();
        assertThat(artifact.getType()).isEqualTo(Artifact.Type.MERMAID);
    }

    @Test
    void getArtifactReturns404ForUnknownId() {
        when(sessionManager.getSession("s1"))
                .thenReturn(Mono.just(Optional.of(session("s1", "u1", "default", message("user", "hi")))));

        assertThatThrownBy(() -> artifactService
                        .getArtifact("s1", "html-9", "u1", "default")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportMermaidProducesSelfContainedEscapedSvg() {
        Artifact artifact = Artifact.builder()
                .id("mermaid-0")
                .type(Artifact.Type.MERMAID)
                .code("graph TD; A-->B & <C>")
                .build();

        String svg = artifactService.exportMermaid(artifact, "svg").block();

        assertThat(svg).startsWith("<?xml");
        assertThat(svg).contains("<pre class=\"mermaid\">");
        assertThat(svg).contains("graph TD; A--&gt;B &amp; &lt;C&gt;");
        assertThat(svg).doesNotContain("<C>");
        assertThat(svg).contains("mermaid.min.js");
    }

    @Test
    void exportMermaidRejectsNonMermaidArtifact() {
        Artifact artifact =
                Artifact.builder().type(Artifact.Type.HTML).code("<div/>").build();

        assertThatThrownBy(() -> artifactService.exportMermaid(artifact, "svg").block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exportMermaidRejectsPngFormat() {
        Artifact artifact =
                Artifact.builder().type(Artifact.Type.MERMAID).code("graph TD;").build();

        assertThatThrownBy(() -> artifactService.exportMermaid(artifact, "png").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PNG");
    }
}
