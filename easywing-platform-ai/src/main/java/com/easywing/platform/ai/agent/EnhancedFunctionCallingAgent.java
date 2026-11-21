package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.*;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ParallelToolExecutor;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 增强版 Function Calling Agent - 完整支持 OpenAI Tools API
 */
@Slf4j
@Component
public class EnhancedFunctionCallingAgent {

    private final AiProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final ParallelToolExecutor parallelToolExecutor;
    private final ObjectMapper objectMapper;

    public EnhancedFunctionCallingAgent(
            AiProviderRegistry providerRegistry,
            ToolExecutor toolExecutor,
            ParallelToolExecutor parallelToolExecutor,
            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.toolExecutor = toolExecutor;
        this.parallelToolExecutor = parallelToolExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 Function Calling Agent（完整版）
     */
    public Mono<AgentResponse> execute(AgentRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        AgentRequest.AgentConfig config = request.getConfig() != null
                ? request.getConfig()
                : AgentRequest.AgentConfig.builder()
                        .maxIterations(10)
                        .maxToolCalls(20)
                        .timeoutMs(60000)
                        .model("gpt-4o")
                        .temperature(0.7)
                        .build();

        List<AgentIteration> iterations = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>(request.getMessages() != null ? request.getMessages() : List.of());

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
                    metadata.put("mode", "enhanced_function_calling");

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
                    log.error("Enhanced function calling failed", e);
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
     * Function Calling 循环执行（完整版）
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

        // 转换工具为 OpenAI Tools 格式
        List<ToolDefinition> tools = toolExecutor.getAvailableTools();
        List<OpenAiTool> openAiTools = convertToOpenAiTools(tools);

        // 构建请求
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(messages)
                .temperature(config.getTemperature())
                .tools(openAiTools)
                .toolChoice("auto") // 自动决定是否调用工具
                .build();

        long iterationStartTime = System.currentTimeMillis();

        return providerRegistry.chat(completionRequest).flatMap(response -> {
            long iterationTime = System.currentTimeMillis() - iterationStartTime;

            ChatMessage assistantMessage = extractMessage(response);
            if (assistantMessage == null) {
                return Mono.just(messages);
            }

            messages.add(assistantMessage);

            // 检查是否有工具调用
            List<OpenAiToolCall> toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 没有工具调用，返回最终答案
                AgentIteration iteration = AgentIteration.builder()
                        .iterationNumber(currentIteration)
                        .executionTimeMs(iterationTime)
                        .finalAnswer(true)
                        .answer(assistantMessage.getContent())
                        .build();
                iterations.add(iteration);

                return Mono.just(messages);
            }

            // 有工具调用，执行工具
            log.info("LLM requested {} tool calls", toolCalls.size());

            // 转换为并行工具调用格式
            List<ParallelToolExecutor.ToolCall> parallelCalls = toolCalls.stream()
                    .map(tc -> ParallelToolExecutor.ToolCall.builder()
                            .callId(tc.getId())
                            .toolName(tc.getFunction().getName())
                            .arguments(parseArguments(tc.getFunction().getArguments()))
                            .build())
                    .collect(Collectors.toList());

            // 并行执行所有工具
            return parallelToolExecutor.executeParallel(parallelCalls).flatMap(results -> {
                // 将工具执行结果添加到消息历史
                for (ParallelToolExecutor.ToolExecutionResult result : results) {
                    ChatMessage toolMessage = ChatMessage.builder()
                            .role("tool")
                            .toolCallId(result.getCallId())
                            .name(result.getToolName())
                            .content(formatToolResult(result))
                            .build();
                    messages.add(toolMessage);
                }

                // 记录迭代
                AgentIteration iteration = AgentIteration.builder()
                        .iterationNumber(currentIteration)
                        .executionTimeMs(iterationTime)
                        .action("parallel_tool_calls")
                        .finalAnswer(false)
                        .build();
                iterations.add(iteration);

                // 继续下一轮
                return executeFunctionCallingLoop(messages, config, iterations, currentIteration + 1);
            });
        });
    }

    /**
     * 转换工具为 OpenAI Tools 格式
     */
    private List<OpenAiTool> convertToOpenAiTools(List<ToolDefinition> tools) {
        return tools.stream()
                .map(tool -> OpenAiTool.builder()
                        .type("function")
                        .function(OpenAiTool.FunctionDefinition.builder()
                                .name(tool.getName())
                                .description(tool.getDescription())
                                .parameters(
                                        tool.getParametersSchema() != null
                                                ? tool.getParametersSchema()
                                                : createDefaultParameters())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Object> createDefaultParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        return params;
    }

    /**
     * 解析工具参数
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse tool arguments: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * 格式化工具结果为字符串
     */
    private String formatToolResult(ParallelToolExecutor.ToolExecutionResult result) {
        if (!result.isSuccess()) {
            return String.format("Error: %s", result.getError());
        }
        try {
            return objectMapper.writeValueAsString(result.getResult());
        } catch (Exception e) {
            return result.getResult().toString();
        }
    }

    private ChatMessage extractMessage(ChatCompletionResponse response) {
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            return response.getChoices().get(0).getMessage();
        }
        return null;
    }
}
