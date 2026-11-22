package com.easywing.platform.ai.observability;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.cost.CostOptimizationEngine;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.model.OpenAiToolCall;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import com.easywing.platform.ai.session.SessionOwnership;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Context Usage 面板（PRD 4.8）。
 * <p>
 * 按会话把上下文占用拆成「指令 / 对话 / 工具 / 缓存」四部分，结合模型目录的
 * {@code contextWindow}、{@code maxOutputTokens} 计算剩余额度与占用比例，并用
 * {@link UsageLedger} 给出该会话模型的累计成本。所有数字均为本地估算，不额外调用模型。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class ContextUsageService {

    private static final double TOKENS_PER_MILLION = 1_000_000.0;

    private final SessionManager sessionManager;
    private final AiProviderRegistry providerRegistry;
    private final UsageLedger usageLedger;
    private final AiProperties aiProperties;
    private final CostOptimizationEngine costOptimizationEngine;

    public ContextUsageService(
            SessionManager sessionManager,
            AiProviderRegistry providerRegistry,
            UsageLedger usageLedger,
            AiProperties aiProperties,
            CostOptimizationEngine costOptimizationEngine) {
        this.sessionManager = sessionManager;
        this.providerRegistry = providerRegistry;
        this.usageLedger = usageLedger;
        this.aiProperties = aiProperties;
        this.costOptimizationEngine = costOptimizationEngine;
    }

    /**
     * 单会话的上下文占用；非本会话（跨用户/租户）返回 404。
     */
    public Mono<ContextUsage> getSessionUsage(String sessionId, String userId, String tenantId) {
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> SessionOwnership.isOwned(session, userId, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在")))
                .map(session -> toUsage(session, billedCost(userId, tenantId, session.getModel())));
    }

    /**
     * 当前用户在当前租户下的全部会话聚合。
     */
    public Mono<ContextUsageSummary> getUserUsage(String userId, String tenantId) {
        return sessionManager
                .getUserSessions(userId)
                .map(sessions -> sessions.stream()
                        .filter(session -> SessionOwnership.normalizeTenant(session.getTenantId())
                                .equals(SessionOwnership.normalizeTenant(tenantId)))
                        .toList())
                .map(sessions -> {
                    List<ContextUsage> usages = sessions.stream()
                            .map(session -> toUsage(session, billedCost(userId, tenantId, session.getModel())))
                            .toList();
                    return buildSummary(usages, billedCost(userId, tenantId, null));
                });
    }

    /**
     * 平台整体聚合（管理员）。
     */
    public Mono<ContextUsageSummary> getPlatformUsage() {
        List<ContextUsage> usages = usageLedger.getRecords().stream()
                .map(record -> new ContextUsage(
                        null,
                        null,
                        record.getUserId(),
                        record.getTenantId(),
                        record.getProvider(),
                        record.getModel(),
                        contextWindow(record.getModel()),
                        maxOutputTokens(record.getModel()),
                        0,
                        0,
                        0,
                        0,
                        record.getTotalTokens(),
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        record.getEstimatedCostUsd(),
                        record.getEstimatedCostUsd(),
                        0))
                .toList();
        return Mono.just(
                buildSummary(usages, usageLedger.buildReport().getSummary().getTotalCostUsd()));
    }

    private ContextUsage toUsage(ConversationSession session, double billedCost) {
        int conversation = 0;
        int tool = 0;
        int instruction = 0;
        int cache = 0;

        for (ChatMessage message : Optional.ofNullable(session.getMessages()).orElse(List.of())) {
            if (message == null) {
                continue;
            }
            String role = message.getRole();
            int content = estimate(contentOf(message));
            if ("system".equalsIgnoreCase(role)) {
                instruction += content;
            } else if ("tool".equalsIgnoreCase(role)) {
                tool += content;
            } else {
                conversation += content;
            }
            tool += toolTokens(message);
            cache += cacheTokens(message);
        }

        int total = conversation + tool + instruction;
        int contextWindow = contextWindow(session.getModel());
        int maxOutput = maxOutputTokens(session.getModel());

        return new ContextUsage(
                session.getId(),
                session.getName(),
                session.getUserId(),
                SessionOwnership.normalizeTenant(session.getTenantId()),
                session.getProvider(),
                session.getModel(),
                contextWindow,
                maxOutput,
                conversation,
                tool,
                instruction,
                cache,
                total,
                Math.max(0, contextWindow - total),
                ratio(total, contextWindow),
                ratio(conversation, total),
                ratio(tool, total),
                ratio(instruction, total),
                estimatedCost(session.getModel(), conversation, tool, instruction, cache),
                billedCost,
                session.getMessageCount());
    }

    private ContextUsageSummary buildSummary(List<ContextUsage> usages, double billedCost) {
        long conversation = 0;
        long tool = 0;
        long instruction = 0;
        long cache = 0;
        long total = 0;
        long totalWindow = 0;
        double estimated = 0.0;

        for (ContextUsage usage : usages) {
            conversation += usage.conversationTokens();
            tool += usage.toolTokens();
            instruction += usage.instructionTokens();
            cache += usage.cacheTokens();
            total += usage.totalTokens();
            totalWindow += usage.contextWindow();
            estimated += usage.estimatedCostUsd();
        }

        return new ContextUsageSummary(
                usages.size(),
                totalWindow,
                conversation,
                tool,
                instruction,
                cache,
                total,
                ratio(total, totalWindow),
                estimated,
                billedCost,
                usages);
    }

    /**
     * 账本中该用户/租户（可选模型）的累计成本，作为「已计费成本」的实时来源。
     */
    private double billedCost(String userId, String tenantId, String model) {
        List<UsageRecord> records =
                userId == null ? usageLedger.getRecords() : usageLedger.getUserRecords(userId, tenantId);
        return records.stream()
                .filter(record -> model == null || model.equals(record.getModel()))
                .mapToDouble(UsageRecord::getEstimatedCostUsd)
                .sum();
    }

    private int toolTokens(ChatMessage message) {
        StringBuilder sb = new StringBuilder();
        if (message.getToolCalls() != null) {
            for (OpenAiToolCall call : message.getToolCalls()) {
                if (call != null) {
                    sb.append(nullToEmpty(
                                    call.getFunction() == null
                                            ? null
                                            : call.getFunction().getName()))
                            .append(' ')
                            .append(argumentsOf(call.getFunction()));
                }
            }
        }
        if (message.getToolCall() != null) {
            sb.append(nullToEmpty(
                            message.getToolCall().getFunction() == null
                                    ? null
                                    : message.getToolCall().getFunction().getName()))
                    .append(' ')
                    .append(argumentsOf(message.getToolCall().getFunction()));
        }
        return estimate(sb.toString());
    }

    private String argumentsOf(OpenAiToolCall.FunctionCall function) {
        return function == null ? "" : nullToEmpty(function.getArguments());
    }

    private String argumentsOf(ChatMessage.Function function) {
        return function == null ? "" : nullToEmpty(function.getArguments());
    }

    private int cacheTokens(ChatMessage message) {
        Map<String, Object> metadata = message.getMetadata();
        if (metadata == null) {
            return 0;
        }
        int total = 0;
        for (String key : new String[] {"cacheReadTokens", "cachedTokens", "cache_read_tokens", "cached_tokens"}) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                total += number.intValue();
            }
        }
        return Math.max(0, total);
    }

    private int estimate(String text) {
        return StringUtils.hasText(text) ? costOptimizationEngine.estimateTokens(text) : 0;
    }

    private String contentOf(ChatMessage message) {
        if (message.getContent() != null) {
            return message.getContent();
        }
        return message.getToolCall() == null
                ? ""
                : argumentsOf(message.getToolCall().getFunction());
    }

    private int contextWindow(String model) {
        return modelInfo(model).map(ModelInfo::getContextWindow).orElse(defaultContextWindow());
    }

    private int maxOutputTokens(String model) {
        return modelInfo(model).map(ModelInfo::getMaxOutputTokens).orElse(0);
    }

    private Optional<ModelInfo> modelInfo(String model) {
        if (!StringUtils.hasText(model)) {
            return Optional.empty();
        }
        return providerRegistry.findModelInfo(model);
    }

    private int defaultContextWindow() {
        AiProperties.ContextUsageConfig config = aiProperties.getContextUsage();
        return config != null && config.getDefaultContextWindow() > 0 ? config.getDefaultContextWindow() : 128_000;
    }

    /**
     * 按输入/输出混合单价估算成本：指令与工具流量按输入价，对话与缓存按输入价的折扣口径。
     */
    private double estimatedCost(String model, int conversation, int tool, int instruction, int cache) {
        Optional<ModelInfo> info = modelInfo(model);
        if (info.isEmpty()) {
            return 0.0;
        }
        double input = info.get().getInputCostPer1MTokens();
        if (input <= 0) {
            return 0.0;
        }
        return (conversation + tool + instruction + cache) * input / TOKENS_PER_MILLION;
    }

    private static double ratio(long part, long whole) {
        return whole <= 0 ? 0.0 : part * 1.0 / whole;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 单会话上下文占用。
     *
     * @param usageRatio        {@code totalTokens / contextWindow}
     * @param conversationRatio {@code conversationTokens / totalTokens}
     * @param toolRatio         {@code toolTokens / totalTokens}
     * @param instructionRatio  {@code instructionTokens / totalTokens}
     */
    public record ContextUsage(
            String sessionId,
            String sessionName,
            String userId,
            String tenantId,
            String provider,
            String model,
            int contextWindow,
            int maxOutputTokens,
            int conversationTokens,
            int toolTokens,
            int instructionTokens,
            int cacheTokens,
            int totalTokens,
            int remainingTokens,
            double usageRatio,
            double conversationRatio,
            double toolRatio,
            double instructionRatio,
            double estimatedCostUsd,
            double billedCostUsd,
            int messageCount) {}

    /** 上下文占用聚合。 */
    public record ContextUsageSummary(
            int sessionCount,
            long totalContextWindow,
            long conversationTokens,
            long toolTokens,
            long instructionTokens,
            long cacheTokens,
            long totalTokens,
            double usageRatio,
            double estimatedCostUsd,
            double billedCostUsd,
            List<ContextUsage> sessions) {}
}
