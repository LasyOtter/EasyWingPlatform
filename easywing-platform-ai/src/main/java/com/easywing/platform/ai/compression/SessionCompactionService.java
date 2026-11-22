package com.easywing.platform.ai.compression;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.cost.CostOptimizationEngine;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.OpenAiToolCall;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import com.easywing.platform.ai.session.SessionOwnership;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 会话上下文手动压缩（PRD 4.9）。
 * <p>
 * 与 {@link ContextCompressor} 不同，本服务直接作用于持久化的 {@link ConversationSession}：
 * 保留全部 system 消息与最近若干条消息，把更早的对话历史交给模型生成摘要（无可用模型时
 * 回退到规则摘要），再写回会话。整个过程受 {@code easywing.ai.compression.enabled} 开关控制，
 * 目标 token 预算与保留条数均可通过请求覆盖。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class SessionCompactionService {

    private static final String SUMMARY_PROMPT =
            """
            你是上下文压缩助手。请把下面的对话历史压缩成一段简洁的中文摘要，\
            保留关键事实、用户意图、已做出的决定与未完成的任务，不要输出与摘要无关的内容。""";
    private static final String SUMMARY_PREFIX = "[已压缩的历史摘要]\n";
    private static final int DEFAULT_TARGET_TOKENS = 8000;
    private static final int DEFAULT_KEEP_LAST_MESSAGES = 6;
    private static final int SUMMARY_MAX_TOKENS = 512;
    private static final int MAX_TRANSCRIPT_CHARS = 20_000;

    private final SessionManager sessionManager;
    private final AiProviderRegistry providerRegistry;
    private final AiProperties aiProperties;
    private final CostOptimizationEngine costOptimizationEngine;

    public SessionCompactionService(
            SessionManager sessionManager,
            AiProviderRegistry providerRegistry,
            AiProperties aiProperties,
            CostOptimizationEngine costOptimizationEngine) {
        this.sessionManager = sessionManager;
        this.providerRegistry = providerRegistry;
        this.aiProperties = aiProperties;
        this.costOptimizationEngine = costOptimizationEngine;
    }

    /**
     * 压缩指定会话；非本会话（跨用户/租户）返回 404，参数非法返回 400。
     *
     * @param targetTokens     目标 token 预算，为空时取 {@code easywing.ai.compression.target-tokens}
     * @param keepLastMessages 完整保留的最近消息条数，为空时取 {@code keep-last-messages}
     */
    public Mono<CompactionResult> compact(
            String sessionId, String userId, String tenantId, Integer targetTokens, Integer keepLastMessages) {
        AiProperties.CompressionConfig config = aiProperties.getCompression();
        boolean enabled = config == null || config.isEnabled();
        int target = targetTokens != null ? targetTokens : defaultTarget(config);
        int keepLast = keepLastMessages != null ? keepLastMessages : defaultKeepLast(config);

        if (target <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetTokens 必须大于 0"));
        }
        if (keepLast < 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "keepLastMessages 不能为负数"));
        }

        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> SessionOwnership.isOwned(session, userId, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在")))
                .flatMap(session -> compact(session, enabled, target, keepLast));
    }

    private Mono<CompactionResult> compact(ConversationSession session, boolean enabled, int target, int keepLast) {
        List<ChatMessage> messages =
                new ArrayList<>(Optional.ofNullable(session.getMessages()).orElse(List.of()));
        int previousTokens = estimate(messages);

        if (!enabled) {
            return Mono.just(skipped(session, messages.size(), previousTokens, target, "compression disabled"));
        }
        if (previousTokens <= target) {
            return Mono.just(skipped(session, messages.size(), previousTokens, target, "within budget"));
        }

        List<ChatMessage> system = messages.stream()
                .filter(message -> isRole(message, ChatMessage.ROLE_SYSTEM))
                .toList();
        List<ChatMessage> body = messages.stream()
                .filter(message -> !isRole(message, ChatMessage.ROLE_SYSTEM))
                .toList();
        int keep = Math.min(keepLast, body.size());
        List<ChatMessage> head = body.subList(0, body.size() - keep);
        List<ChatMessage> tail = new ArrayList<>(body.subList(body.size() - keep, body.size()));

        if (head.isEmpty()) {
            return Mono.just(skipped(session, messages.size(), previousTokens, target, "nothing to summarize"));
        }

        return summarize(session, head)
                .map(summary -> {
                    List<ChatMessage> compressed = new ArrayList<>(system);
                    compressed.add(ChatMessage.builder()
                            .role(ChatMessage.ROLE_SYSTEM)
                            .content(SUMMARY_PREFIX + summary)
                            .build());
                    compressed.addAll(tail);

                    session.setMessages(compressed);
                    session.setMessageCount(compressed.size());
                    session.setUpdatedAt(System.currentTimeMillis());
                    int compactedTokens = estimate(compressed);
                    return (CompactionResult) new CompactionResult(
                            session.getId(),
                            true,
                            messages.size(),
                            compressed.size(),
                            previousTokens,
                            compactedTokens,
                            Math.max(0, previousTokens - compactedTokens),
                            target,
                            "summarized",
                            summary);
                })
                .flatMap(result -> sessionManager.updateSession(session).thenReturn(result));
    }

    /**
     * 优先生成模型摘要；模型不可用或调用失败时回退到规则摘要，保证压缩始终可用。
     */
    private Mono<String> summarize(ConversationSession session, List<ChatMessage> head) {
        String model = session.getModel();
        if (!StringUtils.hasText(model) || !providerRegistry.supportsModel(model)) {
            return Mono.just(deterministicSummary(head));
        }

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .provider(session.getProvider())
                .userId(session.getUserId())
                .tenantId(session.getTenantId())
                .maxTokens(SUMMARY_MAX_TOKENS)
                .messages(List.of(
                        ChatMessage.builder()
                                .role(ChatMessage.ROLE_SYSTEM)
                                .content(SUMMARY_PROMPT)
                                .build(),
                        ChatMessage.builder()
                                .role(ChatMessage.ROLE_USER)
                                .content(transcript(head))
                                .build()))
                .build();

        return providerRegistry
                .chat(request)
                .map(SessionCompactionService::textOf)
                .filter(StringUtils::hasText)
                .onErrorResume(error -> {
                    log.warn("LLM 摘要失败，回退规则摘要: {}", error.getMessage());
                    return Mono.empty();
                })
                .defaultIfEmpty(deterministicSummary(head));
    }

    private String deterministicSummary(List<ChatMessage> head) {
        long user = head.stream()
                .filter(message -> isRole(message, ChatMessage.ROLE_USER))
                .count();
        long assistant = head.stream()
                .filter(message -> isRole(message, ChatMessage.ROLE_ASSISTANT))
                .count();
        long tool = head.stream()
                .filter(message -> isRole(message, ChatMessage.ROLE_TOOL))
                .count();
        return String.format("已省略 %d 条历史消息（用户 %d / 助手 %d / 工具 %d）。", head.size(), user, assistant, tool);
    }

    private String transcript(List<ChatMessage> head) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : head) {
            sb.append(StringUtils.hasText(message.getRole()) ? message.getRole() : "unknown")
                    .append(": ")
                    .append(compactContent(message))
                    .append('\n');
            if (sb.length() > MAX_TRANSCRIPT_CHARS) {
                sb.append("...(truncated)");
                break;
            }
        }
        return sb.toString();
    }

    private int estimate(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            total += estimateTokens(message.getRole()) + estimateTokens(compactContent(message));
            if (message.getName() != null) {
                total += estimateTokens(message.getName());
            }
            if (message.getToolCalls() != null) {
                for (OpenAiToolCall call : message.getToolCalls()) {
                    if (call != null && call.getFunction() != null) {
                        total += estimateTokens(call.getFunction().getName())
                                + estimateTokens(call.getFunction().getArguments());
                    }
                }
            }
        }
        return total;
    }

    private String compactContent(ChatMessage message) {
        if (StringUtils.hasText(message.getContent())) {
            return message.getContent();
        }
        if (message.getToolCall() != null && message.getToolCall().getFunction() != null) {
            return message.getToolCall().getFunction().getArguments();
        }
        return "";
    }

    private int estimateTokens(String text) {
        return StringUtils.hasText(text) ? costOptimizationEngine.estimateTokens(text) : 0;
    }

    private static String textOf(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null) {
            return null;
        }
        return response.getChoices().stream()
                .filter(choice -> choice != null && choice.getMessage() != null)
                .map(ChatCompletionResponse.Choice::getMessage)
                .map(ChatMessage::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static boolean isRole(ChatMessage message, String role) {
        return message != null && role.equalsIgnoreCase(message.getRole());
    }

    private CompactionResult skipped(
            ConversationSession session, int messageCount, int tokens, int target, String reason) {
        return new CompactionResult(
                session.getId(), false, messageCount, messageCount, tokens, tokens, 0, target, reason, null);
    }

    private int defaultTarget(AiProperties.CompressionConfig config) {
        return config != null && config.getTargetTokens() > 0 ? config.getTargetTokens() : DEFAULT_TARGET_TOKENS;
    }

    private int defaultKeepLast(AiProperties.CompressionConfig config) {
        return config != null && config.getKeepLastMessages() >= 0
                ? config.getKeepLastMessages()
                : DEFAULT_KEEP_LAST_MESSAGES;
    }

    /**
     * 压缩结果。
     *
     * @param compacted           是否真的发生压缩（预算已满足 / 功能关闭时为 false）
     * @param previousMessageCount 压缩前消息条数
     * @param messageCount         压缩后消息条数
     * @param previousTokens       压缩前估算 token
     * @param tokens               压缩后估算 token
     * @param savedTokens          节省的估算 token
     * @param targetTokens         目标预算
     * @param reason               结果说明（summarized / within budget / compression disabled / nothing to summarize）
     * @param summary              摘要正文，未压缩时为 null
     */
    public record CompactionResult(
            String sessionId,
            boolean compacted,
            int previousMessageCount,
            int messageCount,
            int previousTokens,
            int tokens,
            int savedTokens,
            int targetTokens,
            String reason,
            String summary) {}
}
