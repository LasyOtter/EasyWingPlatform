package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * ReAct Agent - 实现 Reasoning + Acting 模式
 * 通过思考-行动-观察的循环来解决问题
 */
@Slf4j
@Component
public class ReactAgent {

    private final AiProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    // ReAct 提示词模板
    private static final String REACT_SYSTEM_PROMPT =
            """
            你是一个智能助手，可以使用工具来回答问题。请遵循以下格式：

            问题: 用户的输入问题
            思考: 你应该思考要做什么
            行动: 要使用的工具名称，必须是 [{tool_names}] 之一
            行动输入: 工具的输入参数，JSON 格式
            观察: 工具执行的结果
            ... (这个思考/行动/行动输入/观察可以重复 N 次)
            思考: 我现在知道最终答案了
            最终答案: 对原始问题的最终答案

            可用工具:
            {tool_descriptions}

            开始!

            问题: {input}
            思考:""";

    private final ReactParser parser;

    public ReactAgent(AiProviderRegistry providerRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.parser = new ReactParser(objectMapper);
    }

    /**
     * 执行 ReAct Agent
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

        long startTime = System.currentTimeMillis();

        return executeReactLoop(request.getPrompt(), config, iterations, messages, 0)
                .map(finalAnswer -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    // 构建最终响应消息
                    ChatMessage responseMessage = ChatMessage.builder()
                            .role("assistant")
                            .content(finalAnswer)
                            .build();

                    messages.add(responseMessage);

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("iterations", iterations);
                    metadata.put("iterationCount", iterations.size());
                    metadata.put("totalExecutionTime", totalTime);

                    return AgentResponse.builder()
                            .id(UUID.randomUUID().toString())
                            .agentId(request.getAgentId())
                            .sessionId(sessionId)
                            .message(responseMessage)
                            .messages(messages)
                            .completed(true)
                            .finishReason("completed")
                            .metadata(metadata)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("ReAct agent execution failed", e);
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
     * ReAct 循环执行
     */
    private Mono<String> executeReactLoop(
            String input,
            AgentRequest.AgentConfig config,
            List<AgentIteration> iterations,
            List<ChatMessage> conversationHistory,
            int currentIteration) {

        if (currentIteration >= config.getMaxIterations()) {
            log.warn("Reached max iterations: {}", config.getMaxIterations());
            return Mono.just("抱歉，已达到最大迭代次数限制，无法完成任务。");
        }

        if (iterations.size() >= config.getMaxToolCalls()) {
            log.warn("Reached max tool calls: {}", config.getMaxToolCalls());
            return Mono.just("抱歉，已达到最大工具调用次数限制。");
        }

        // 构建 ReAct 提示词
        String prompt = buildReactPrompt(input, iterations);

        // 调用 LLM
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(List.of(
                        ChatMessage.builder().role("user").content(prompt).build()))
                .temperature(config.getTemperature())
                .build();

        long iterationStartTime = System.currentTimeMillis();

        return providerRegistry.chat(completionRequest).flatMap(response -> {
            String llmResponse = extractContent(response);
            long iterationTime = System.currentTimeMillis() - iterationStartTime;

            log.info("Iteration {} response: {}", currentIteration, llmResponse);

            // 解析 LLM 响应
            return parseAndExecute(llmResponse, config, iterations, currentIteration, iterationTime)
                    .flatMap(result -> {
                        if (result.isFinalAnswer()) {
                            return Mono.just(result.getAnswer());
                        } else {
                            // 继续下一次迭代
                            return executeReactLoop(
                                    input, config, iterations, conversationHistory, currentIteration + 1);
                        }
                    });
        });
    }

    /**
     * 解析 LLM 响应并执行工具
     */
    private Mono<ParseResult> parseAndExecute(
            String llmResponse,
            AgentRequest.AgentConfig config,
            List<AgentIteration> iterations,
            int iterationNumber,
            long iterationTime) {

        AgentIteration iteration = AgentIteration.builder()
                .iterationNumber(iterationNumber)
                .executionTimeMs(iterationTime)
                .build();

        final ReactParser.Parsed parsed;
        try {
            parsed = parser.parse(llmResponse);
        } catch (ReactParser.ParseException e) {
            // 明确报错：不把解析失败伪装成最终答案返回给用户
            log.warn("ReAct 响应解析失败: {}", e.getMessage());
            iteration.setError("无法解析模型响应: " + e.getMessage());
            iterations.add(iteration);
            return Mono.just(new ParseResult(true, "抱歉，无法理解如何执行该任务: " + e.getMessage()));
        }

        if (parsed.finalAnswerFound()) {
            iteration.setThought(parsed.thought());
            iteration.setFinalAnswer(true);
            iteration.setAnswer(parsed.finalAnswer());
            iterations.add(iteration);

            return Mono.just(new ParseResult(true, parsed.finalAnswer()));
        }

        iteration.setThought(parsed.thought());
        iteration.setAction(parsed.action());
        iteration.setActionInput(parsed.actionInput());

        String action = parsed.action();
        Map<String, Object> actionInput = parsed.actionInput();

        // 执行工具
        return toolExecutor
                .executeTool(action, actionInput)
                .map(result -> {
                    String observation = formatObservation(result);
                    iteration.setObservation(observation);
                    iteration.setFinalAnswer(false);
                    iterations.add(iteration);

                    return new ParseResult(false, null);
                })
                .onErrorResume(e -> {
                    String errorMsg = "工具执行失败: " + e.getMessage();
                    iteration.setObservation(errorMsg);
                    iteration.setError(errorMsg);
                    iterations.add(iteration);

                    return Mono.just(new ParseResult(false, null));
                });
    }

    /**
     * 构建 ReAct 提示词
     */
    private String buildReactPrompt(String input, List<AgentIteration> iterations) {
        List<ToolDefinition> tools = toolExecutor.getAvailableTools();

        String toolNames = tools.stream().map(ToolDefinition::getName).collect(Collectors.joining(", "));

        String toolDescriptions = tools.stream()
                .map(t -> String.format("- %s: %s", t.getName(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        StringBuilder prompt = new StringBuilder(REACT_SYSTEM_PROMPT
                .replace("{tool_names}", toolNames)
                .replace("{tool_descriptions}", toolDescriptions)
                .replace("{input}", input));

        // 添加之前的迭代历史
        for (AgentIteration iter : iterations) {
            if (iter.getThought() != null) {
                prompt.append(iter.getThought()).append("\n");
            }
            if (iter.getAction() != null) {
                prompt.append("行动: ").append(iter.getAction()).append("\n");
            }
            if (iter.getActionInput() != null) {
                try {
                    prompt.append("行动输入: ")
                            .append(objectMapper.writeValueAsString(iter.getActionInput()))
                            .append("\n");
                } catch (Exception e) {
                    prompt.append("行动输入: ").append(iter.getActionInput()).append("\n");
                }
            }
            if (iter.getObservation() != null) {
                prompt.append("观察: ").append(iter.getObservation()).append("\n");
            }
            prompt.append("思考: ");
        }

        return prompt.toString();
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            ChatMessage message = response.getChoices().get(0).getMessage();
            if (message != null && message.getContent() != null) {
                return message.getContent();
            }
        }
        return "";
    }

    private String formatObservation(Map<String, Object> result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    private static class ParseResult {
        private final boolean isFinalAnswer;
        private final String answer;

        public ParseResult(boolean isFinalAnswer, String answer) {
            this.isFinalAnswer = isFinalAnswer;
            this.answer = answer;
        }

        public boolean isFinalAnswer() {
            return isFinalAnswer;
        }

        public String getAnswer() {
            return answer;
        }
    }
}
