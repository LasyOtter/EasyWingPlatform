package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.multiagent.AgentRole;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务分解与委派 Agent - 自动将复杂任务分解并委派给最合适的 Agent
 */
@Slf4j
@Component
public class TaskDecompositionAgent {

    private final AgentExecutor agentExecutor;
    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentRole> availableAgents;

    public TaskDecompositionAgent(
            AgentExecutor agentExecutor, AiProviderRegistry providerRegistry, ObjectMapper objectMapper) {
        this.agentExecutor = agentExecutor;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
        this.availableAgents = initializeAgents();
    }

    /**
     * 初始化可用 Agent
     */
    private Map<String, AgentRole> initializeAgents() {
        Map<String, AgentRole> agents = new HashMap<>();

        // 规划专家
        agents.put(
                "planner",
                AgentRole.builder()
                        .id("planner")
                        .name("规划专家")
                        .description("擅长任务规划和分解")
                        .capabilities(List.of("任务分解", "优先级排序", "资源分配"))
                        .domains(List.of("项目管理", "系统设计"))
                        .priority(10)
                        .build());

        // 技术专家
        agents.put(
                "tech_expert",
                AgentRole.builder()
                        .id("tech_expert")
                        .name("技术专家")
                        .description("擅长技术方案设计和实现")
                        .capabilities(List.of("架构设计", "代码实现", "技术选型"))
                        .domains(List.of("软件开发", "系统架构"))
                        .priority(9)
                        .build());

        // 数据分析师
        agents.put(
                "data_analyst",
                AgentRole.builder()
                        .id("data_analyst")
                        .name("数据分析师")
                        .description("擅长数据分析和可视化")
                        .capabilities(List.of("数据处理", "统计分析", "报告生成"))
                        .domains(List.of("数据科学", "商业智能"))
                        .priority(8)
                        .build());

        // 内容创作者
        agents.put(
                "content_creator",
                AgentRole.builder()
                        .id("content_creator")
                        .name("内容创作者")
                        .description("擅长内容创作和文案写作")
                        .capabilities(List.of("文案写作", "内容策划", "SEO优化"))
                        .domains(List.of("市场营销", "内容运营"))
                        .priority(7)
                        .build());

        // 质量检查员
        agents.put(
                "qa_engineer",
                AgentRole.builder()
                        .id("qa_engineer")
                        .name("质量检查员")
                        .description("擅长质量检查和测试")
                        .capabilities(List.of("质量检查", "测试设计", "缺陷分析"))
                        .domains(List.of("软件测试", "质量保证"))
                        .priority(6)
                        .build());

        return agents;
    }

    /**
     * 执行任务分解与委派
     */
    public Mono<TaskDecompositionResult> executeWithDecomposition(AgentRequest request) {
        log.info("Starting task decomposition for: {}", request.getPrompt());

        long startTime = System.currentTimeMillis();
        String requestedModel =
                request.getConfig() == null ? null : request.getConfig().getModel();

        // 第一步：分析任务
        String userId = request.getUserId();
        String tenantId = request.getTenantId();
        return analyzeTask(request.getPrompt(), requestedModel, userId, tenantId)
                .flatMap(analysis -> {
                    log.info(
                            "Task analysis complete, complexity: {}, estimated subtasks: {}",
                            analysis.getComplexity(),
                            analysis.getEstimatedSubtasks());

                    // 如果任务简单，直接执行
                    if (analysis.getComplexity().equals("simple")) {
                        return agentExecutor.execute(request).map(response -> TaskDecompositionResult.builder()
                                .originalTask(request.getPrompt())
                                .complexity("simple")
                                .subtasks(List.of())
                                .finalResult(response.getMessage().getContent())
                                .totalExecutionTime(System.currentTimeMillis() - startTime)
                                .build());
                    }

                    // 第二步：分解为子任务
                    return decomposeTask(request.getPrompt(), analysis, requestedModel, userId, tenantId)
                            .flatMap(subtasks -> {
                                log.info("Task decomposed into {} subtasks", subtasks.size());

                                // 第三步：为每个子任务选择 Agent
                                return assignAgents(subtasks).flatMap(assignments -> {
                                    log.info("Agents assigned for all subtasks");

                                    // 第四步：执行所有子任务
                                    return executeSubtasks(assignments, request).flatMap(results -> {
                                        // 第五步：合并结果
                                        return synthesizeResults(
                                                        results, request.getPrompt(), requestedModel, userId, tenantId)
                                                .map(finalResult -> {
                                                    long totalTime = System.currentTimeMillis() - startTime;

                                                    return TaskDecompositionResult.builder()
                                                            .originalTask(request.getPrompt())
                                                            .complexity(analysis.getComplexity())
                                                            .subtasks(subtasks)
                                                            .assignments(assignments)
                                                            .subtaskResults(results)
                                                            .finalResult(finalResult)
                                                            .totalExecutionTime(totalTime)
                                                            .build();
                                                });
                                    });
                                });
                            });
                });
    }

    /**
     * 分析任务
     */
    private Mono<TaskAnalysis> analyzeTask(String task, String model, String userId, String tenantId) {
        String analysisPrompt = String.format(
                """
                请分析以下任务：
                %s

                请评估：
                1. 任务复杂度（simple/medium/complex）
                2. 是否需要分解为子任务
                3. 预估子任务数量
                4. 所需技能领域

                返回 JSON 格式：
                {
                  "complexity": "medium",
                  "needsDecomposition": true,
                  "estimatedSubtasks": 3,
                  "requiredDomains": ["技术", "设计"]
                }
                """,
                task);

        return callLLM(analysisPrompt, model, userId, tenantId).map(this::parseTaskAnalysis);
    }

    /**
     * 分解任务
     */
    private Mono<List<SubTask>> decomposeTask(
            String task, TaskAnalysis analysis, String model, String userId, String tenantId) {
        String decompositionPrompt = String.format(
                """
                任务: %s

                请将此任务分解为 %d 个可独立执行的子任务。

                返回 JSON 格式：
                {
                  "subtasks": [
                    {
                      "id": "subtask_1",
                      "description": "子任务描述",
                      "requiredCapabilities": ["能力1", "能力2"],
                      "estimatedComplexity": "medium",
                      "dependencies": []
                    }
                  ]
                }
                """,
                task, analysis.getEstimatedSubtasks());

        return callLLM(decompositionPrompt, model, userId, tenantId).map(this::parseSubTasks);
    }

    /**
     * 分配 Agent
     */
    private Mono<List<AgentAssignment>> assignAgents(List<SubTask> subtasks) {
        return Flux.fromIterable(subtasks)
                .map(subtask -> {
                    AgentRole bestAgent = selectBestAgent(subtask);
                    return AgentAssignment.builder()
                            .subtask(subtask)
                            .assignedAgent(bestAgent)
                            .matchScore(calculateMatchScore(subtask, bestAgent))
                            .build();
                })
                .collectList();
    }

    /**
     * 选择最佳 Agent
     */
    private AgentRole selectBestAgent(SubTask subtask) {
        return availableAgents.values().stream()
                .max(Comparator.comparingDouble(agent -> calculateMatchScore(subtask, agent)))
                .orElse(availableAgents.get("planner"));
    }

    /**
     * 计算匹配分数
     */
    private double calculateMatchScore(SubTask subtask, AgentRole agent) {
        double score = 0.0;

        // 能力匹配
        for (String required : subtask.getRequiredCapabilities()) {
            if (agent.getCapabilities().stream().anyMatch(cap -> cap.contains(required) || required.contains(cap))) {
                score += 30.0;
            }
        }

        // 领域匹配
        if (agent.getDomains() != null) {
            score += agent.getDomains().size() * 10.0;
        }

        // 优先级加权
        score += agent.getPriority() * 5.0;

        return score;
    }

    /**
     * 执行子任务
     */
    private Mono<List<SubTaskResult>> executeSubtasks(List<AgentAssignment> assignments, AgentRequest originalRequest) {

        return Flux.fromIterable(assignments)
                .concatMap(assignment -> executeSubtask(assignment, originalRequest))
                .collectList();
    }

    /**
     * 执行单个子任务
     */
    private Mono<SubTaskResult> executeSubtask(AgentAssignment assignment, AgentRequest originalRequest) {

        log.info(
                "Executing subtask: {} with agent: {}",
                assignment.getSubtask().getDescription(),
                assignment.getAssignedAgent().getName());

        long startTime = System.currentTimeMillis();

        AgentRequest subtaskRequest = AgentRequest.builder()
                .agentId(assignment.getAssignedAgent().getId())
                .prompt(assignment.getSubtask().getDescription())
                .config(originalRequest.getConfig())
                .build();

        return agentExecutor
                .execute(subtaskRequest)
                .map(response -> {
                    long duration = System.currentTimeMillis() - startTime;

                    return SubTaskResult.builder()
                            .subtaskId(assignment.getSubtask().getId())
                            .description(assignment.getSubtask().getDescription())
                            .assignedAgent(assignment.getAssignedAgent().getName())
                            .result(response.getMessage().getContent())
                            .success(response.isCompleted())
                            .executionTime(duration)
                            .build();
                })
                .onErrorResume(e -> {
                    long duration = System.currentTimeMillis() - startTime;

                    return Mono.just(SubTaskResult.builder()
                            .subtaskId(assignment.getSubtask().getId())
                            .description(assignment.getSubtask().getDescription())
                            .assignedAgent(assignment.getAssignedAgent().getName())
                            .success(false)
                            .error(e.getMessage())
                            .executionTime(duration)
                            .build());
                });
    }

    /**
     * 合成结果
     */
    private Mono<String> synthesizeResults(
            List<SubTaskResult> results, String originalTask, String model, String userId, String tenantId) {
        String successResults = results.stream()
                .filter(SubTaskResult::isSuccess)
                .map(r -> String.format("【%s】\n%s", r.getDescription(), r.getResult()))
                .collect(Collectors.joining("\n\n"));

        String synthesisPrompt = String.format(
                """
                原始任务: %s

                各子任务执行结果:
                %s

                请基于以上结果，提供完整、连贯的最终答案。
                """,
                originalTask, successResults);

        return callLLM(synthesisPrompt, model, userId, tenantId);
    }

    /**
     * 调用 LLM
     */
    private Mono<String> callLLM(String prompt, String requestedModel, String userId, String tenantId) {
        String model = requestedModel;
        if (model == null || !providerRegistry.supportsModel(model)) {
            model = providerRegistry
                    .getDefaultProvider()
                    .flatMap(provider -> provider.getSupportedModels().stream().findFirst())
                    .map(com.easywing.platform.ai.model.ModelInfo::getId)
                    .orElseThrow(() -> new IllegalStateException("No configured model is available for subagents"));
        }
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(
                        ChatMessage.builder().role("user").content(prompt).build()))
                .temperature(0.5)
                .userId(userId)
                .tenantId(tenantId)
                .build();

        return providerRegistry
                .chat(request)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

    /**
     * 解析任务分析
     */
    private TaskAnalysis parseTaskAnalysis(String content) {
        try {
            String json = extractJson(content);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            return TaskAnalysis.builder()
                    .complexity((String) data.get("complexity"))
                    .needsDecomposition((Boolean) data.getOrDefault("needsDecomposition", false))
                    .estimatedSubtasks(((Number) data.getOrDefault("estimatedSubtasks", 1)).intValue())
                    .requiredDomains((List<String>) data.get("requiredDomains"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse task analysis", e);
            return TaskAnalysis.builder()
                    .complexity("simple")
                    .needsDecomposition(false)
                    .estimatedSubtasks(1)
                    .build();
        }
    }

    /**
     * 解析子任务
     */
    private List<SubTask> parseSubTasks(String content) {
        try {
            String json = extractJson(content);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subtasksData = (List<Map<String, Object>>) data.get("subtasks");

            List<SubTask> subtasks = new ArrayList<>();
            for (Map<String, Object> subtaskData : subtasksData) {
                subtasks.add(SubTask.builder()
                        .id((String) subtaskData.get("id"))
                        .description((String) subtaskData.get("description"))
                        .requiredCapabilities((List<String>) subtaskData.get("requiredCapabilities"))
                        .estimatedComplexity((String) subtaskData.getOrDefault("estimatedComplexity", "medium"))
                        .dependencies((List<String>) subtaskData.get("dependencies"))
                        .build());
            }

            return subtasks;
        } catch (Exception e) {
            log.error("Failed to parse subtasks", e);
            return List.of();
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskAnalysis {
        private String complexity;
        private boolean needsDecomposition;
        private int estimatedSubtasks;
        private List<String> requiredDomains;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTask {
        private String id;
        private String description;
        private List<String> requiredCapabilities;
        private String estimatedComplexity;
        private List<String> dependencies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentAssignment {
        private SubTask subtask;
        private AgentRole assignedAgent;
        private double matchScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskResult {
        private String subtaskId;
        private String description;
        private String assignedAgent;
        private String result;
        private boolean success;
        private String error;
        private long executionTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDecompositionResult {
        private String originalTask;
        private String complexity;
        private List<SubTask> subtasks;
        private List<AgentAssignment> assignments;
        private List<SubTaskResult> subtaskResults;
        private String finalResult;
        private long totalExecutionTime;
    }
}
