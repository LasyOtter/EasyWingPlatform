package com.easywing.platform.ai.compression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.cost.CostOptimizationEngine;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SessionCompactionServiceTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private AiProviderRegistry providerRegistry;

    @Mock
    private CostOptimizationEngine costOptimizationEngine;

    private AiProperties aiProperties;

    private SessionCompactionService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        service = new SessionCompactionService(sessionManager, providerRegistry, aiProperties, costOptimizationEngine);
        lenient()
                .when(costOptimizationEngine.estimateTokens(anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).length());
        lenient()
                .when(sessionManager.updateSession(any(ConversationSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private static ConversationSession session(
            String userId, String tenantId, String model, List<ChatMessage> messages) {
        return ConversationSession.builder()
                .id("s1")
                .userId(userId)
                .tenantId(tenantId)
                .name("对话")
                .model(model)
                .provider("custom")
                .messageCount(messages.size())
                .messages(new ArrayList<>(messages))
                .build();
    }

    private static List<ChatMessage> history(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessage.ROLE_SYSTEM)
                .content("sys")
                .build());
        for (int i = 0; i < count; i++) {
            String role = i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT;
            messages.add(
                    ChatMessage.builder().role(role).content("x".repeat(50)).build());
        }
        return messages;
    }

    private void stubSession(ConversationSession session) {
        when(sessionManager.getSession("s1")).thenReturn(Mono.just(Optional.of(session)));
    }

    private void stubSummary(String text) {
        when(providerRegistry.supportsModel("m1")).thenReturn(true);
        when(providerRegistry.chat(any(ChatCompletionRequest.class)))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .message(ChatMessage.builder()
                                        .role(ChatMessage.ROLE_ASSISTANT)
                                        .content(text)
                                        .build())
                                .build()))
                        .build()));
    }

    @Test
    void invalidTargetIsRejected() {
        assertThatThrownBy(() -> service.compact("s1", "u1", "default", 0, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("targetTokens");
        verify(sessionManager, never()).getSession(anyString());
    }

    @Test
    void negativeKeepLastIsRejected() {
        assertThatThrownBy(
                        () -> service.compact("s1", "u1", "default", null, -1).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("keepLastMessages");
    }

    @Test
    void otherUsersSessionIsRejected() {
        stubSession(session("u2", "default", "m1", history(4)));

        assertThatThrownBy(
                        () -> service.compact("s1", "u1", "default", 10, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void tenantMismatchIsRejected() {
        stubSession(session("u1", "tenant-a", "m1", history(4)));

        assertThatThrownBy(
                        () -> service.compact("s1", "u1", "tenant-b", 10, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void withinBudgetIsNotCompacted() {
        stubSession(session("u1", "default", "m1", history(2)));

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 100_000, null).block();

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("within budget");
        assertThat(result.messageCount()).isEqualTo(result.previousMessageCount());
        verify(providerRegistry, never()).chat(any(ChatCompletionRequest.class));
        verify(sessionManager, never()).updateSession(any(ConversationSession.class));
    }

    @Test
    void disabledCompressionIsSkipped() {
        aiProperties.getCompression().setEnabled(false);
        stubSession(session("u1", "default", "m1", history(10)));

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 10, null).block();

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("compression disabled");
        verify(sessionManager, never()).updateSession(any(ConversationSession.class));
    }

    @Test
    void nothingToSummarizeWhenEverythingIsKept() {
        stubSession(session("u1", "default", "m1", history(4)));

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 10, 100).block();

        assertThat(result.compacted()).isFalse();
        assertThat(result.reason()).isEqualTo("nothing to summarize");
    }

    @Test
    void compactsWithModelSummaryAndPersists() {
        stubSession(session("u1", "default", "m1", history(10)));
        stubSummary("模型摘要");

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 10, 2).block();

        assertThat(result.compacted()).isTrue();
        assertThat(result.reason()).isEqualTo("summarized");
        assertThat(result.summary()).isEqualTo("模型摘要");
        assertThat(result.previousMessageCount()).isEqualTo(11);
        // system(1) + 摘要(1) + 保留最近 2 条
        assertThat(result.messageCount()).isEqualTo(4);
        assertThat(result.savedTokens()).isGreaterThan(0);

        ArgumentCaptor<ConversationSession> captor = ArgumentCaptor.forClass(ConversationSession.class);
        verify(sessionManager).updateSession(captor.capture());
        ConversationSession saved = captor.getValue();
        assertThat(saved.getMessages()).hasSize(4);
        assertThat(saved.getMessages().get(0).getRole()).isEqualTo(ChatMessage.ROLE_SYSTEM);
        assertThat(saved.getMessages().get(0).getContent()).isEqualTo("sys");
        assertThat(saved.getMessages().get(1).getContent()).startsWith("[已压缩的历史摘要]");
        assertThat(saved.getMessages().get(1).getContent()).contains("模型摘要");
        assertThat(saved.getMessages().get(2).getRole()).isEqualTo(ChatMessage.ROLE_USER);
        assertThat(saved.getMessageCount()).isEqualTo(4);
    }

    @Test
    void fallsBackToDeterministicSummaryWhenModelFails() {
        stubSession(session("u1", "default", "m1", history(6)));
        when(providerRegistry.supportsModel("m1")).thenReturn(true);
        when(providerRegistry.chat(any(ChatCompletionRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 10, 2).block();

        assertThat(result.compacted()).isTrue();
        assertThat(result.summary()).contains("已省略 4 条历史消息");
        verify(sessionManager).updateSession(any(ConversationSession.class));
    }

    @Test
    void fallsBackToDeterministicSummaryWhenModelUnsupported() {
        stubSession(session("u1", "default", null, history(6)));

        SessionCompactionService.CompactionResult result =
                service.compact("s1", "u1", "default", 10, 2).block();

        assertThat(result.compacted()).isTrue();
        assertThat(result.summary()).contains("已省略 4 条历史消息");
        verify(providerRegistry, never()).chat(any(ChatCompletionRequest.class));
    }
}
