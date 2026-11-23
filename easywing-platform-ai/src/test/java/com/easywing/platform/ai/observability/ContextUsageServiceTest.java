package com.easywing.platform.ai.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.cost.CostOptimizationEngine;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.model.OpenAiToolCall;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import java.util.List;
import java.util.Map;
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
class ContextUsageServiceTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private AiProviderRegistry providerRegistry;

    @Mock
    private UsageLedger usageLedger;

    @Mock
    private CostOptimizationEngine costOptimizationEngine;

    private AiProperties aiProperties;

    private ContextUsageService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        service = new ContextUsageService(
                sessionManager, providerRegistry, usageLedger, aiProperties, costOptimizationEngine);
        // 用「字符数 == token 数」的确定性估算，便于断言分类结果
        lenient()
                .when(costOptimizationEngine.estimateTokens(anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).length());
    }

    private static ConversationSession session(String id, String userId, String tenantId, String model) {
        return ConversationSession.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .name("对话")
                .model(model)
                .provider("custom")
                .messageCount(3)
                .messages(List.of(
                        ChatMessage.builder().role("system").content("sys").build(),
                        ChatMessage.builder().role("user").content("hello").build(),
                        ChatMessage.builder().role("assistant").content("world").build()))
                .build();
    }

    private void stubModel(String model, int contextWindow, int maxOutput, double inputCost) {
        when(providerRegistry.findModelInfo(model))
                .thenReturn(Optional.of(ModelInfo.builder()
                        .contextWindow(contextWindow)
                        .maxOutputTokens(maxOutput)
                        .inputCostPer1MTokens(inputCost)
                        .outputCostPer1MTokens(0.0)
                        .build()));
    }

    @Test
    void otherUsersSessionIsRejected() {
        when(sessionManager.getSession("s1")).thenReturn(Mono.just(Optional.of(session("s1", "u2", "default", "m1"))));

        assertThatThrownBy(() -> service.getSessionUsage("s1", "u1", "default").block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void otherTenantsSessionIsRejected() {
        when(sessionManager.getSession("s1")).thenReturn(Mono.just(Optional.of(session("s1", "u1", "tenant-a", "m1"))));

        assertThatThrownBy(() -> service.getSessionUsage("s1", "u1", "tenant-b").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void classifiesSystemUserAndAssistantTokens() {
        stubModel("m1", 1000, 100, 1.0);
        lenient().when(usageLedger.getUserRecords("u1", "default")).thenReturn(List.of());
        when(sessionManager.getSession("s1")).thenReturn(Mono.just(Optional.of(session("s1", "u1", "default", "m1"))));

        ContextUsageService.ContextUsage usage =
                service.getSessionUsage("s1", "u1", "default").block();

        // system=3, user=5, assistant=5
        assertThat(usage.instructionTokens()).isEqualTo(3);
        assertThat(usage.conversationTokens()).isEqualTo(10);
        assertThat(usage.toolTokens()).isZero();
        assertThat(usage.totalTokens()).isEqualTo(13);
        assertThat(usage.remainingTokens()).isEqualTo(987);
        assertThat(usage.usageRatio()).isEqualTo(13.0 / 1000.0);
        assertThat(usage.contextWindow()).isEqualTo(1000);
        assertThat(usage.maxOutputTokens()).isEqualTo(100);
        assertThat(usage.conversationRatio()).isEqualTo(10.0 / 13.0);
        assertThat(usage.instructionRatio()).isEqualTo(3.0 / 13.0);
    }

    @Test
    void countsToolMessagesToolCallsAndCachedTokens() {
        ConversationSession toolSession = ConversationSession.builder()
                .id("s2")
                .userId("u1")
                .tenantId("default")
                .model("m1")
                .provider("custom")
                .messageCount(3)
                .messages(List.of(
                        ChatMessage.builder().role("user").content("go").build(),
                        ChatMessage.builder()
                                .role("assistant")
                                .content("")
                                .toolCalls(List.of(OpenAiToolCall.builder()
                                        .function(OpenAiToolCall.FunctionCall.builder()
                                                .name("lookup")
                                                .arguments("{\"q\":\"x\"}")
                                                .build())
                                        .build()))
                                .build(),
                        ChatMessage.builder()
                                .role("tool")
                                .content("result")
                                .metadata(Map.of("cacheReadTokens", 4))
                                .build()))
                .build();
        stubModel("m1", 1000, 100, 1.0);
        when(sessionManager.getSession("s2")).thenReturn(Mono.just(Optional.of(toolSession)));

        ContextUsageService.ContextUsage usage =
                service.getSessionUsage("s2", "u1", "default").block();

        // assistant toolCall: name "lookup"(6) + 分隔空格(1) + args "{\"q\":\"x\"}"(9) = 16
        // tool message content "result"(6) + cache 4
        assertThat(usage.conversationTokens()).isEqualTo(2); // "go"
        assertThat(usage.toolTokens()).isEqualTo(22); // 16 + 6
        assertThat(usage.cacheTokens()).isEqualTo(4);
        assertThat(usage.totalTokens()).isEqualTo(24); // 2 + 22
    }

    @Test
    void billedCostComesFromLedgerForSameModel() {
        stubModel("m1", 1000, 100, 1.0);
        when(sessionManager.getSession("s1")).thenReturn(Mono.just(Optional.of(session("s1", "u1", "default", "m1"))));
        when(usageLedger.getUserRecords("u1", "default"))
                .thenReturn(List.of(
                        usageRecord("u1", "default", "m1", 0.25),
                        usageRecord("u1", "default", "m1", 0.75),
                        usageRecord("u1", "default", "other-model", 9.0)));

        ContextUsageService.ContextUsage usage =
                service.getSessionUsage("s1", "u1", "default").block();

        assertThat(usage.billedCostUsd()).isEqualTo(1.0);
        assertThat(usage.estimatedCostUsd()).isEqualTo(13.0 / 1_000_000.0);
    }

    @Test
    void userUsageAggregatesAndFiltersTenant() {
        stubModel("m1", 1000, 100, 1.0);
        ConversationSession own = session("s1", "u1", "default", "m1");
        ConversationSession foreign = session("s2", "u1", "tenant-a", "m1");
        when(sessionManager.getUserSessions("u1", "default")).thenReturn(Mono.just(List.of(own, foreign)));
        when(usageLedger.getUserRecords("u1", "default")).thenReturn(List.of(usageRecord("u1", "default", "m1", 0.5)));

        ContextUsageService.ContextUsageSummary summary =
                service.getUserUsage("u1", "default").block();

        assertThat(summary.sessionCount()).isOne();
        assertThat(summary.sessions())
                .extracting(ContextUsageService.ContextUsage::sessionId)
                .containsExactly("s1");
        assertThat(summary.totalTokens()).isEqualTo(13);
        assertThat(summary.totalContextWindow()).isEqualTo(1000);
        assertThat(summary.billedCostUsd()).isEqualTo(0.5);
    }

    @Test
    void platformUsageAggregatesAllLedgerRecords() {
        when(usageLedger.getRecords())
                .thenReturn(
                        List.of(usageRecord("u1", "default", "m1", 0.25), usageRecord("u2", "default", "m1", 0.75)));
        when(usageLedger.buildReport())
                .thenReturn(new UsageLedger.UsageReport(new UsageLedger.UsageSummary(2, 10, 20, 30, 1.0), List.of()));
        stubModel("m1", 1000, 100, 1.0);

        ContextUsageService.ContextUsageSummary summary =
                service.getPlatformUsage().block();

        assertThat(summary.sessionCount()).isEqualTo(2);
        assertThat(summary.billedCostUsd()).isEqualTo(1.0);
        assertThat(summary.totalContextWindow()).isEqualTo(2000);
    }

    @Test
    void unknownModelFallsBackToConfiguredWindow() {
        aiProperties.getContextUsage().setDefaultContextWindow(64_000);
        when(providerRegistry.findModelInfo("mystery")).thenReturn(Optional.empty());
        lenient().when(usageLedger.getUserRecords("u1", "default")).thenReturn(List.of());
        when(sessionManager.getSession("s9"))
                .thenReturn(Mono.just(Optional.of(session("s9", "u1", "default", "mystery"))));

        ContextUsageService.ContextUsage usage =
                service.getSessionUsage("s9", "u1", "default").block();

        assertThat(usage.contextWindow()).isEqualTo(64_000);
        assertThat(usage.remainingTokens()).isEqualTo(64_000 - 13);
        assertThat(usage.estimatedCostUsd()).isZero();
    }

    private static UsageRecord usageRecord(String userId, String tenantId, String model, double cost) {
        return UsageRecord.builder()
                .userId(userId)
                .tenantId(tenantId)
                .model(model)
                .totalTokens(10)
                .estimatedCostUsd(cost)
                .build();
    }
}
