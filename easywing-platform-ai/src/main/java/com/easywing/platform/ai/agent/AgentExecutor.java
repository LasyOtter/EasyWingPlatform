package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Agent 执行器 - 支持简单对话和 ReAct 模式
 */
@Slf4j
@Component
public class AgentExecutor {

    private final AiProviderRegistry providerRegistry;
    private final ReactAgent reactAgent;
    private final FunctionCallingAgent functionCallingAgent;
    private final ToolExecutor toolExecutor;

    public AgentExecutor(
            AiProviderRegistry providerRegistry,
            ReactAgent reactAgent,
            FunctionCallingAgent functionCallingAgent,
            ToolExecutor toolExecutor) {
        this.providerRegistry = providerRegistry;
        this.reactAgent = reactAgent;
        this.functionCallingAgent = functionCallingAgent;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 执行 Agent 请求
     * 根据配置自动选择简单对话模式、ReAct 模式或 Function Calling 模式
     */
    public Mono<AgentResponse> execute(AgentRequest request) {
        String agentId = request.getAgentId() != null ? request.getAgentId() : "default";

        // 检查是否需要使用工具
        boolean useTools = shouldUseToolMode(request);

        if (!useTools) {
            log.info("Using simple chat mode for agent: {}", agentId);
            return executeSimpleChat(request);
        }

        // 判断使用 Function Calling 还是 ReAct 模式
        String mode = determineToolMode(request);

        if ("function_calling".equals(mode)) {
            log.info("Using Function Calling mode for agent: {}", agentId);
            return functionCallingAgent.execute(request);
        } else {
            log.info("Using ReAct mode for agent: {}", agentId);
            return reactAgent.execute(request);
        }
    }

    /**
     * 简单对话模式 - 不使用工具
     */
    private Mono<AgentResponse> executeSimpleChat(AgentRequest request) {
        String agentId = request.getAgentId() != null ? request.getAgentId() : "default";
        List<ChatMessage> messages = new ArrayList<>(request.getMessages() != null ? request.getMessages() : List.of());

        if (request.getPrompt() != null) {
            messages.add(ChatMessage.builder()
                    .role("user")
                    .content(request.getPrompt())
                    .build());
        }

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(
                        request.getConfig() != null && request.getConfig().getModel() != null
                                ? request.getConfig().getModel()
                                : "gpt-4o")
                .messages(messages)
                .temperature(request.getConfig() != null ? request.getConfig().getTemperature() : 0.7)
                .build();

        String finalAgentId = agentId;
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        return providerRegistry
                .chat(completionRequest)
                .map(response -> {
                    ChatMessage assistantMessage = null;
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        assistantMessage = response.getChoices().get(0).getMessage();
                    }

                    messages.add(assistantMessage);

                    return AgentResponse.builder()
                            .id(UUID.randomUUID().toString())
                            .agentId(finalAgentId)
                            .sessionId(sessionId)
                            .message(assistantMessage)
                            .messages(messages)
                            .completed(true)
                            .finishReason("completed")
                            .build();
                })
                .doOnError(e -> log.error("Agent execution failed", e));
    }

    /**
     * 判断是否应该使用工具模式
     */
    private boolean shouldUseToolMode(AgentRequest request) {
        // 如果配置明确指定了 maxToolCalls > 0，使用工具模式
        if (request.getConfig() != null && request.getConfig().getMaxToolCalls() > 0) {
            return true;
        }

        // 如果有可用工具且问题可能需要工具，使用工具模式
        if (!toolExecutor.getAvailableTools().isEmpty()) {
            String prompt = request.getPrompt();
            if (prompt != null) {
                // 简单启发式：包含特定关键词时使用工具
                String lower = prompt.toLowerCase();
                return lower.contains("计算")
                        || lower.contains("查询")
                        || lower.contains("搜索")
                        || lower.contains("时间")
                        || lower.contains("日期")
                        || lower.contains("分析")
                        || lower.contains("处理")
                        || lower.contains("请求");
            }
        }

        return false;
    }

    /**
     * 确定使用哪种工具模式
     */
    private String determineToolMode(AgentRequest request) {
        // 如果明确指定了模式
        if (request.getConfig() != null && request.getConfig().getMetadata() != null) {
            String mode = (String) request.getConfig().getMetadata().get("mode");
            if ("react".equals(mode) || "function_calling".equals(mode)) {
                return mode;
            }
        }

        // 默认优先使用 Function Calling（更稳定）
        // 但如果模型不支持，则回退到 ReAct
        String model = request.getConfig() != null ? request.getConfig().getModel() : "gpt-4o";
        if (model != null && (model.startsWith("gpt-4") || model.startsWith("gpt-3.5"))) {
            return "function_calling";
        }

        // 其他模型使用 ReAct
        return "react";
    }
}
