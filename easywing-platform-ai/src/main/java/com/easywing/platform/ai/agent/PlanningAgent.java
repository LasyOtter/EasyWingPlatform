package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规划 Agent - Plan & Execute 模式
 * 先制定计划，再按计划执行
 */
@Slf4j
@Component
public class PlanningAgent {

    private final AgentExecutor agentExecutor;
    private final AiProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public PlanningAgent(
            AgentExecutor agentExecutor,
            AiProviderRegistry providerRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper) {
        this.agentExecutor = agentExecutor;
        this.providerRegistry = providerRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 带规划的执行
     */
    public Mono<AgentResponse> executeWithPlanning(AgentRequest request) {
        log.info("Starting planning execution for task: {}", request.getPrompt());

        long startTime = System.currentTimeMillis();

        // 第一步：制定计划
        return createPlan(request)
                .flatMap(plan -> {
                    log.info("Plan created with {} steps", plan.getSteps().size());

                    // 第二步：执行计划
                    return executePlan(plan, request)
                            .flatMap(results -> {
                                // 第三步：验证结果
                                return verifyResults(results, request.getPrompt())
                                        .map(finalResult -> {
                                            long totalTime = System.currentTimeMillis() - startTime;

                                            Map<String, Object> metadata = new HashMap<>();
                                            metadata.put("plan", plan);
                                            metadata.put("executionResults", results);
                                            metadata.put("totalExecutionTime", totalTime);
                                            metadata.put("mode", "planning");

                                            return AgentResponse.builder()
                                                    .id(UUID.randomUUID().toString())
                                                    .agentId(request.getAgentId())
                                                    .sessionId(request.getSessionId())
                                                    .message(ChatMessage.builder()
                                                            .role("assistant")
                                                            .content(finalResult)
                                                            .build())
                                                    .completed(true)
                                                    .finishReason("completed")
                                                    .metadata(metadata)
                                                    .build();
                                        });
                            });
                });
    }

    /**
     * 制定执行计划
     */
    private Mono<ExecutionPlan> createPlan(AgentRequest request) {
        List<ToolDefinition> availableTools = toolExecutor.getAvailableTools();

        String planningPrompt = buildPlanningPrompt(request.getPrompt(), availableTools);

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        ChatMessage.builder()
                                .role("system")
                                .content("你是一个任务规划专家，擅长将复杂任务分解为可执行的步骤。")
                                .build(),
                        ChatMessage.builder()
                                .role("user")
                                .content(planningPrompt)
                                .build()
                ))
                .temperature(0.3)
                .build();

        return providerRegistry.chat(completionRequest)
                .map(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    return parsePlan(content);
                });
    }

    /**
     * 构建规划提示词
     */
    private String buildPlanningPrompt(String task, List<ToolDefinition> tools) {
        String toolList = tools.stream()
                .map(t -> String.format("- %s: %s", t.getName(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                任务: %s

                可用工具:
                %s

                请制定详细的执行计划，将任务分解为具体的步骤。

                请按以下 JSON 格式返回计划:
                ```json
                {
                  "goal": "任务目标描述",
                  "steps": [
                    {
                      "stepNumber": 1,
                      "description": "步骤描述",
                      "action": "执行的动作",
                      "tool": "使用的工具名称（如果需要）",
                      "expectedOutput": "预期输出",
                      "dependencies": []
                    }
                  ],
                  "estimatedTime": "预估时间（秒）",
                  "complexity": "simple|medium|complex"
                }
                ```

                规划原则:
                1. 步骤要具体、可执行
                2. 合理使用可用工具
                3. 考虑步骤之间的依赖关系
                4. 估算执行时间
                """, task, toolList);
    }

    /**
     * 解析计划
     */
    private ExecutionPlan parsePlan(String content) {
        try {
            String json = extractJson(content);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            List<PlanStep> steps = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsData = (List<Map<String, Object>>) data.get("steps");

            for (Map<String, Object> stepData : stepsData) {
                steps.add(PlanStep.builder()
                        .stepNumber(((Number) stepData.get("stepNumber")).intValue())
                        .description((String) stepData.get("description"))
                        .action((String) stepData.get("action"))
                        .tool((String) stepData.get("tool"))
                        .expectedOutput((String) stepData.get("expectedOutput"))
                        .dependencies((List<Integer>) stepData.get("dependencies"))
                        .build());
            }

            return ExecutionPlan.builder()
                    .goal((String) data.get("goal"))
                    .steps(steps)
                    .estimatedTime(data.containsKey("estimatedTime") ?
                            Integer.parseInt(data.get("estimatedTime").toString()) : 60)
                    .complexity((String) data.getOrDefault("complexity", "medium"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse plan, using fallback", e);
            return createFallbackPlan(content);
        }
    }

    /**
     * 创建后备计划
     */
    private ExecutionPlan createFallbackPlan(String content) {
        return ExecutionPlan.builder()
                .goal("执行任务")
                .steps(List.of(
                        PlanStep.builder()
                                .stepNumber(1)
                                .description("直接执行任务")
                                .action("execute")
                                .build()
                ))
                .complexity("simple")
                .build();
    }

    /**
     * 执行计划
     */
    private Mono<List<StepResult>> executePlan(ExecutionPlan plan, AgentRequest request) {
        log.info("Executing plan with {} steps", plan.getSteps().size());

        List<StepResult> results = new ArrayList<>();

        return Flux.fromIterable(plan.getSteps())
                .concatMap(step -> executeStep(step, results, request))
                .collectList();
    }

    /**
     * 执行单个步骤
     */
    private Mono<StepResult> executeStep(
            PlanStep step,
            List<StepResult> previousResults,
            AgentRequest request) {

        log.info("Executing step {}: {}", step.getStepNumber(), step.getDescription());

        long startTime = System.currentTimeMillis();

        // 检查依赖
        if (step.getDependencies() != null && !step.getDependencies().isEmpty()) {
            for (Integer dep : step.getDependencies()) {
                if (previousResults.stream().noneMatch(r -> r.getStepNumber() == dep && r.isSuccess())) {
                    return Mono.just(StepResult.builder()
                            .stepNumber(step.getStepNumber())
                            .success(false)
                            .error("依赖步骤 " + dep + " 未成功")
                            .build());
                }
            }
        }

        // 如果指定了工具，直接调用工具
        if (step.getTool() != null && !step.getTool().isEmpty()) {
            return executeStepWithTool(step)
                    .map(result -> {
                        long duration = System.currentTimeMillis() - startTime;
                        StepResult stepResult = StepResult.builder()
                                .stepNumber(step.getStepNumber())
                                .description(step.getDescription())
                                .result(result)
                                .success(true)
                                .executionTime(duration)
                                .build();
                        previousResults.add(stepResult);
                        return stepResult;
                    })
                    .onErrorResume(e -> {
                        long duration = System.currentTimeMillis() - startTime;
                        StepResult stepResult = StepResult.builder()
                                .stepNumber(step.getStepNumber())
                                .description(step.getDescription())
                                .success(false)
                                .error(e.getMessage())
                                .executionTime(duration)
                                .build();
                        previousResults.add(stepResult);
                        return Mono.just(stepResult);
                    });
        }

        // 否则用 Agent 执行
        String stepPrompt = buildStepPrompt(step, previousResults);

        AgentRequest stepRequest = AgentRequest.builder()
                .agentId(request.getAgentId())
                .sessionId(request.getSessionId())
                .prompt(stepPrompt)
                .config(request.getConfig())
                .build();

        return agentExecutor.execute(stepRequest)
                .map(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    StepResult stepResult = StepResult.builder()
                            .stepNumber(step.getStepNumber())
                            .description(step.getDescription())
                            .result(response.getMessage().getContent())
                            .success(response.isCompleted())
                            .executionTime(duration)
                            .build();
                    previousResults.add(stepResult);
                    return stepResult;
                });
    }

    /**
     * 使用工具执行步骤
     */
    private Mono<String> executeStepWithTool(PlanStep step) {
        // 简化的工具参数提取（实际应该更智能）
        Map<String, Object> arguments = new HashMap<>();

        return toolExecutor.executeTool(step.getTool(), arguments)
                .map(result -> {
                    try {
                        return objectMapper.writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        return String.valueOf(result);
                    }
                })
                .onErrorResume(e -> Mono.just("工具执行失败: " + e.getMessage()));
    }

    /**
     * 构建步骤提示词
     */
    private String buildStepPrompt(PlanStep step, List<StepResult> previousResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【当前步骤】\n");
        prompt.append("步骤 ").append(step.getStepNumber()).append(": ");
        prompt.append(step.getDescription()).append("\n\n");

        if (!previousResults.isEmpty()) {
            prompt.append("【前序步骤结果】\n");
            previousResults.forEach(result -> {
                prompt.append(String.format("步骤 %d: %s\n",
                        result.getStepNumber(), result.getResult()));
            });
            prompt.append("\n");
        }

        prompt.append("请完成当前步骤。");

        return prompt.toString();
    }

    /**
     * 验证结果
     */
    private Mono<String> verifyResults(List<StepResult> results, String originalTask) {
        // 汇总所有步骤的结果
        String summary = results.stream()
                .filter(StepResult::isSuccess)
                .map(r -> String.format("步骤 %d: %s",
                        r.getStepNumber(), r.getResult()))
                .collect(Collectors.joining("\n"));

        String verificationPrompt = String.format("""
                原始任务: %s

                执行结果:
                %s

                请基于以上步骤结果，提供完整的最终答案。
                """, originalTask, summary);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.builder()
                        .role("user")
                        .content(verificationPrompt)
                        .build()))
                .temperature(0.5)
                .build();

        return providerRegistry.chat(request)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    /**
     * 执行计划
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionPlan {
        private String goal;
        private List<PlanStep> steps;
        private int estimatedTime;
        private String complexity;
    }

    /**
     * 计划步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanStep {
        private int stepNumber;
        private String description;
        private String action;
        private String tool;
        private String expectedOutput;
        private List<Integer> dependencies;
    }

    /**
     * 步骤结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private int stepNumber;
        private String description;
        private String result;
        private boolean success;
        private String error;
        private long executionTime;
    }
}
