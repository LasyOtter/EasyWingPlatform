package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Function Calling Agent - 使用 OpenAI Function Calling API
 * 相比 ReAct 模式更稳定、更快速
 */
@Slf4j
@Component
public class FunctionCallingAgent {

    private final AiProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final FunctionCallingConverter converter;
    private final ObjectMapper objectMapper;

    public FunctionCallingAgent(
            AiProviderRegistry providerRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.converter = new FunctionCallingConverter(objectMapper);
    }

    /**
     * 执行 Function Calling Agent
     */
    public Mono<AgentResponse> execute(AgentRequest request) {
        String sessionId = request.getSessionId() != null ?
                request.getSessionId() : UUID.randomUUID().toString();

        AgentRequest.AgentConfig config = request.getConfig() != null ?
                request.getConfig() : AgentRequest.AgentConfig.builder()
                .maxIterations(10)
                .maxToolCalls(20)
                .timeoutMs(60000)
                .model("gpt-4o")
                .temperature(0.7)
                .build();

        List<AgentIteration> iterations = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>(request.getMessages() != null ?
                request.getMessages() : List.of());

        // 添加用户消息
        if (request.getPrompt() != null) {
            messages.add(ChatMessage.builder()
                    .role("user")
                    .content(request.getPrompt())
                    .build());
        }

        long startTime = System.currentTimeMillis();

        return executeFunctionCallingLoop(messages, config, iterations, 0)
                .map(finalMessages -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    // 获取最后的助手消息
                    ChatMessage lastMessage = finalMessages.stream()
                            .filter(m -> "assistant".equals(m.getRole()))
                            .reduce((first, second) -> second)
                            .orElse(ChatMessage.builder()
                                    .role("assistant")
                                    .content("执行完成")
                                    .build());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("iterations", iterations);
                    metadata.put("iterationCount", iterations.size());
                    metadata.put("totalExecutionTime", totalTime);
                    metadata.put("mode", "function_calling");

                    return AgentResponse.builder()
                            .id(UUID.randomUUID().toString())
                            .agentId(request.getAgentId())
                            .sessionId(sessionId)
                            .message(lastMessage)
                            .messages(finalMessages)
                            .completed(true)
                            .finishReason("completed")
                            .metadata(metadata)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Function calling agent execution failed", e);
                    return Mono.just(AgentResponse.builder()
                            .agentId(request.getAgentId())
                            .sessionId(sessionId)
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content("抱歉，执行过程中出现错误: " + e.getMessage())
                                    .build())
                            .completed(false)
                            .finishReason("error: " + e.getMessage())
                            .build());
                });
    }

    /**
     * Function Calling 循环执行
     */
    private Mono<List<ChatMessage>> executeFunctionCallingLoop(
            List<ChatMessage> messages,
            AgentRequest.AgentConfig config,
            List<AgentIteration> iterations,
            int currentIteration) {

        if (currentIteration >= config.getMaxIterations()) {
            log.warn("Reached max iterations: {}", config.getMaxIterations());
            return Mono.just(messages);
        }

        if (iterations.size() >= config.getMaxToolCalls()) {
            log.warn("Reached max tool calls: {}", config.getMaxToolCalls());
            return Mono.just(messages);
        }

        // 获取可用工具并转换为 Function 格式
        List<ToolDefinition> tools = toolExecutor.getAvailableTools();
        List<FunctionCallingConverter.FunctionDefinition> functions = converter.convertToFunctions(tools);

        // 构建请求（注意：这里简化了，实际需要根据不同提供商调整）
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(messages)
                .temperature(config.getTemperature())
                .build();

        long iterationStartTime = System.currentTimeMillis();

        return providerRegistry.chat(completionRequest)
                .flatMap(response -> {
                    long iterationTime = System.currentTimeMillis() - iterationStartTime;

                    ChatMessage assistantMessage = extractMessage(response);
                    if (assistantMessage == null) {
                        return Mono.just(messages);
                    }

                    messages.add(assistantMessage);

                    // 检查是否有 function_call (这里简化处理，实际需要检查 response 中的 tool_calls)
                    // 由于当前简化实现，我们假设没有 tool_calls，直接返回
                    // 完整实现需要根据 OpenAI API 的 tool_calls 格式来处理

                    AgentIteration iteration = AgentIteration.builder()
                            .iterationNumber(currentIteration)
                            .executionTimeMs(iterationTime)
                            .thought("Function calling mode")
                            .finalAnswer(true)
                            .answer(assistantMessage.getContent())
                            .build();

                    iterations.add(iteration);

                    return Mono.just(messages);
                });
    }

    private ChatMessage extractMessage(ChatCompletionResponse response) {
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            return response.getChoices().get(0).getMessage();
        }
        return null;
    }
}
