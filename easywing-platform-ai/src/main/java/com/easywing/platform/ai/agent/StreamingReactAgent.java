package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 流式 ReAct Agent - 实时推送执行过程
 */
@Slf4j
@Component
public class StreamingReactAgent {

    private final AiProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("思考[:：]\\s*(.+?)(?=行动[:：]|最终答案[:：]|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile("行动[:：]\\s*([^\\n]+)");
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("行动输入[:：]\\s*(.+?)(?=观察[:：]|思考[:：]|$)", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile("最终答案[:：]\\s*(.+)", Pattern.DOTALL);

    public StreamingReactAgent(
            AiProviderRegistry providerRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式执行 Agent
     */
    public Flux<AgentStreamEvent> executeStream(AgentRequest request) {
        Sinks.Many<AgentStreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

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

        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        // 发送开始事件
        sink.tryEmitNext(AgentStreamEvent.start());

        executeReactLoopStream(request.getPrompt(), config, iterations, messages, 0, sink)
                .subscribe(
                        finalAnswer -> {
                            // 发送最终答案
                            sink.tryEmitNext(AgentStreamEvent.finalAnswer(finalAnswer));

                            // 构建完整响应
                            AgentResponse response = AgentResponse.builder()
                                    .id(UUID.randomUUID().toString())
                                    .agentId(request.getAgentId())
                                    .sessionId(sessionId)
                                    .message(ChatMessage.builder()
                                            .role("assistant")
                                            .content(finalAnswer)
                                            .build())
                                    .messages(messages)
                                    .completed(true)
                                    .finishReason("completed")
                                    .metadata(Map.of("iterations", iterations, "iterationCount", iterations.size()))
                                    .build();

                            // 发送完成事件
                            sink.tryEmitNext(AgentStreamEvent.complete(response));
                            sink.tryEmitComplete();
                        },
                        error -> {
                            log.error("Streaming execution failed", error);
                            sink.tryEmitNext(AgentStreamEvent.error(error.getMessage()));
                            sink.tryEmitComplete();
                        });

        return sink.asFlux();
    }

    /**
     * 流式 ReAct 循环
     */
    private reactor.core.publisher.Mono<String> executeReactLoopStream(
            String input,
            AgentRequest.AgentConfig config,
            List<AgentIteration> iterations,
            List<ChatMessage> conversationHistory,
            int currentIteration,
            Sinks.Many<AgentStreamEvent> sink) {

        if (currentIteration >= config.getMaxIterations()) {
            log.warn("Reached max iterations: {}", config.getMaxIterations());
            return reactor.core.publisher.Mono.just("已达到最大迭代次数限制。");
        }

        if (iterations.size() >= config.getMaxToolCalls()) {
            log.warn("Reached max tool calls: {}", config.getMaxToolCalls());
            return reactor.core.publisher.Mono.just("已达到最大工具调用次数限制。");
        }

        String prompt = buildReactPrompt(input, iterations);

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(List.of(
                        ChatMessage.builder().role("user").content(prompt).build()))
                .temperature(config.getTemperature())
                .build();

        return providerRegistry.chat(completionRequest).flatMap(response -> {
            String llmResponse = extractContent(response);

            // 检查最终答案
            Matcher finalAnswerMatcher = FINAL_ANSWER_PATTERN.matcher(llmResponse);
            if (finalAnswerMatcher.find()) {
                String finalAnswer = finalAnswerMatcher.group(1).trim();
                return reactor.core.publisher.Mono.just(finalAnswer);
            }

            // 提取思考
            Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(llmResponse);
            if (thoughtMatcher.find()) {
                String thought = thoughtMatcher.group(1).trim();
                sink.tryEmitNext(AgentStreamEvent.thinking(thought, currentIteration));
            }

            // 提取行动
            Matcher actionMatcher = ACTION_PATTERN.matcher(llmResponse);
            if (!actionMatcher.find()) {
                return reactor.core.publisher.Mono.just("无法理解如何执行该任务。");
            }

            String action = actionMatcher.group(1).trim();

            // 提取行动输入
            Matcher actionInputMatcher = ACTION_INPUT_PATTERN.matcher(llmResponse);
            Map<String, Object> actionInput = new HashMap<>();
            if (actionInputMatcher.find()) {
                String actionInputStr = actionInputMatcher.group(1).trim();
                try {
                    actionInput = objectMapper.readValue(actionInputStr, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    actionInput.put("input", actionInputStr);
                }
            }

            // 发送工具调用开始事件
            sink.tryEmitNext(AgentStreamEvent.toolCallStart(action, actionInput, currentIteration));

            // 执行工具
            Map<String, Object> finalActionInput = actionInput;
            return toolExecutor
                    .executeTool(action, actionInput)
                    .flatMap(result -> {
                        String observation = formatObservation(result);

                        // 发送工具调用结束事件
                        sink.tryEmitNext(AgentStreamEvent.toolCallEnd(action, result, currentIteration));

                        // 发送观察事件
                        sink.tryEmitNext(AgentStreamEvent.observation(observation, currentIteration));

                        AgentIteration iteration = AgentIteration.builder()
                                .iterationNumber(currentIteration)
                                .action(action)
                                .actionInput(finalActionInput)
                                .observation(observation)
                                .finalAnswer(false)
                                .build();

                        iterations.add(iteration);

                        // 继续下一次迭代
                        return executeReactLoopStream(
                                input, config, iterations, conversationHistory, currentIteration + 1, sink);
                    })
                    .onErrorResume(e -> {
                        String errorMsg = "工具执行失败: " + e.getMessage();
                        sink.tryEmitNext(AgentStreamEvent.error(errorMsg));
                        return reactor.core.publisher.Mono.just(errorMsg);
                    });
        });
    }

    private String buildReactPrompt(String input, List<AgentIteration> iterations) {
        List<ToolDefinition> tools = toolExecutor.getAvailableTools();

        String toolNames = tools.stream()
                .map(ToolDefinition::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        String toolDescriptions = tools.stream()
                .map(t -> String.format("- %s: %s", t.getName(), t.getDescription()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String template =
                """
                你是一个智能助手，可以使用工具来回答问题。请遵循以下格式：

                问题: 用户的输入问题
                思考: 你应该思考要做什么
                行动: 要使用的工具名称，必须是 [%s] 之一
                行动输入: 工具的输入参数，JSON 格式
                观察: 工具执行的结果
                ... (这个思考/行动/行动输入/观察可以重复 N 次)
                思考: 我现在知道最终答案了
                最终答案: 对原始问题的最终答案

                可用工具:
                %s

                开始!

                问题: %s
                思考:""";

        StringBuilder prompt = new StringBuilder(String.format(template, toolNames, toolDescriptions, input));

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

    private String extractContent(com.easywing.platform.ai.model.ChatCompletionResponse response) {
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
}
