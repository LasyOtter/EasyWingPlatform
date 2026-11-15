package com.easywing.platform.ai.multiagent;

import com.easywing.platform.ai.agent.AgentExecutor;
import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多 Agent 协作服务
 */
@Slf4j
@Service
public class MultiAgentService {

    private final AgentExecutor agentExecutor;
    private final Map<String, AgentRole> agentRoles = new HashMap<>();

    public MultiAgentService(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
        initializeDefaultRoles();
    }

    /**
     * 初始化默认角色
     */
    private void initializeDefaultRoles() {
        // 规划者
        agentRoles.put(
                "planner",
                AgentRole.builder()
                        .id("planner")
                        .name("规划者")
                        .description("负责任务分解和规划")
                        .systemPrompt("你是一个任务规划专家，擅长将复杂任务分解为可执行的子任务。")
                        .capabilities(List.of("任务分解", "优先级排序", "资源分配"))
                        .build());

        // 研究者
        agentRoles.put(
                "researcher",
                AgentRole.builder()
                        .id("researcher")
                        .name("研究者")
                        .description("负责信息收集和研究")
                        .systemPrompt("你是一个研究专家，擅长收集和分析信息。")
                        .capabilities(List.of("信息检索", "数据分析", "报告撰写"))
                        .build());

        // 执行者
        agentRoles.put(
                "executor",
                AgentRole.builder()
                        .id("executor")
                        .name("执行者")
                        .description("负责具体任务执行")
                        .systemPrompt("你是一个任务执行专家，擅长使用工具完成具体任务。")
                        .capabilities(List.of("工具调用", "任务执行", "结果验证"))
                        .build());

        // 评审者
        agentRoles.put(
                "reviewer",
                AgentRole.builder()
                        .id("reviewer")
                        .name("评审者")
                        .description("负责质量检查和评审")
                        .systemPrompt("你是一个质量评审专家，擅长检查和改进工作成果。")
                        .capabilities(List.of("质量检查", "结果评估", "改进建议"))
                        .build());
    }

    /**
     * 顺序协作 - 多个 Agent 按顺序执行
     */
    public Mono<MultiAgentResponse> sequentialCollaboration(MultiAgentRequest request) {
        log.info(
                "Starting sequential collaboration with {} agents",
                request.getAgentIds().size());

        List<AgentResponse> responses = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        return executeSequentially(request.getAgentIds(), request.getTask(), responses, 0, request)
                .map(finalResult -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    return MultiAgentResponse.builder()
                            .collaborationType(CollaborationType.SEQUENTIAL)
                            .agentResponses(responses)
                            .finalResult(finalResult)
                            .totalExecutionTimeMs(totalTime)
                            .build();
                });
    }

    /**
     * 并行协作 - 多个 Agent 同时执行
     */
    public Mono<MultiAgentResponse> parallelCollaboration(MultiAgentRequest request) {
        log.info(
                "Starting parallel collaboration with {} agents",
                request.getAgentIds().size());

        long startTime = System.currentTimeMillis();

        return Flux.fromIterable(request.getAgentIds())
                .flatMap(agentId -> executeAgent(agentId, request.getTask(), request))
                .collectList()
                .map(responses -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    // 合并所有 Agent 的响应
                    String finalResult = synthesizeResponses(responses);

                    return MultiAgentResponse.builder()
                            .collaborationType(CollaborationType.PARALLEL)
                            .agentResponses(responses)
                            .finalResult(finalResult)
                            .totalExecutionTimeMs(totalTime)
                            .build();
                });
    }

    /**
     * 辩论协作 - Agent 之间进行多轮讨论
     */
    public Mono<MultiAgentResponse> debateCollaboration(MultiAgentRequest request) {
        log.info(
                "Starting debate collaboration with {} agents, {} rounds",
                request.getAgentIds().size(),
                request.getMaxRounds());

        List<DebateRound> debateRounds = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        return executeDebate(request, debateRounds, 0).map(finalResult -> {
            long totalTime = System.currentTimeMillis() - startTime;

            return MultiAgentResponse.builder()
                    .collaborationType(CollaborationType.DEBATE)
                    .debateRounds(debateRounds)
                    .finalResult(finalResult)
                    .totalExecutionTimeMs(totalTime)
                    .build();
        });
    }

    /**
     * 层级协作 - 分层执行（规划 → 执行 → 评审）
     */
    public Mono<MultiAgentResponse> hierarchicalCollaboration(MultiAgentRequest request) {
        log.info("Starting hierarchical collaboration");

        long startTime = System.currentTimeMillis();
        List<AgentResponse> allResponses = new ArrayList<>();

        // 阶段1：规划
        return executeAgent("planner", "请为以下任务制定执行计划：" + request.getTask(), request)
                .flatMap(plannerResponse -> {
                    allResponses.add(plannerResponse);
                    String plan = plannerResponse.getMessage().getContent();

                    // 阶段2：执行
                    return executeAgent("executor", "请根据以下计划执行任务：\n" + plan, request)
                            .flatMap(executorResponse -> {
                                allResponses.add(executorResponse);
                                String result = executorResponse.getMessage().getContent();

                                // 阶段3：评审
                                return executeAgent("reviewer", "请评审以下执行结果：\n" + result, request)
                                        .map(reviewerResponse -> {
                                            allResponses.add(reviewerResponse);
                                            long totalTime = System.currentTimeMillis() - startTime;

                                            return MultiAgentResponse.builder()
                                                    .collaborationType(CollaborationType.HIERARCHICAL)
                                                    .agentResponses(allResponses)
                                                    .finalResult(reviewerResponse
                                                            .getMessage()
                                                            .getContent())
                                                    .totalExecutionTimeMs(totalTime)
                                                    .build();
                                        });
                            });
                });
    }

    /**
     * 顺序执行 Agent
     */
    private Mono<String> executeSequentially(
            List<String> agentIds, String task, List<AgentResponse> responses, int index, MultiAgentRequest request) {

        if (index >= agentIds.size()) {
            // 所有 Agent 执行完成
            return Mono.just(
                    responses.isEmpty()
                            ? ""
                            : responses.get(responses.size() - 1).getMessage().getContent());
        }

        String agentId = agentIds.get(index);

        // 构建带上下文的任务
        String contextualTask = buildContextualTask(task, responses);

        return executeAgent(agentId, contextualTask, request).flatMap(response -> {
            responses.add(response);
            return executeSequentially(agentIds, task, responses, index + 1, request);
        });
    }

    /**
     * 执行单个 Agent
     */
    private Mono<AgentResponse> executeAgent(String agentId, String task, MultiAgentRequest request) {
        AgentRole role = agentRoles.get(agentId);
        if (role == null) {
            log.warn("Agent role not found: {}, using default", agentId);
        }

        // 构建带系统提示的消息
        List<com.easywing.platform.ai.model.ChatMessage> messages = new ArrayList<>();
        if (role != null && role.getSystemPrompt() != null) {
            messages.add(com.easywing.platform.ai.model.ChatMessage.builder()
                    .role("system")
                    .content(role.getSystemPrompt())
                    .build());
        }

        AgentRequest agentRequest = AgentRequest.builder()
                .agentId(agentId)
                .prompt(task)
                .messages(messages)
                .config(AgentRequest.AgentConfig.builder()
                        .model(request.getModel() != null ? request.getModel() : "gpt-4o")
                        .temperature(request.getTemperature())
                        .maxToolCalls(request.getMaxToolCalls())
                        .build())
                .build();

        return agentExecutor.execute(agentRequest);
    }

    /**
     * 执行辩论
     */
    private Mono<String> executeDebate(MultiAgentRequest request, List<DebateRound> debateRounds, int round) {

        if (round >= request.getMaxRounds()) {
            // 辩论结束，让评审者总结
            String debateHistory = buildDebateHistory(debateRounds);
            return executeAgent("reviewer", "请总结以下辩论并给出最终结论：\n" + debateHistory, request)
                    .map(response -> response.getMessage().getContent());
        }

        DebateRound currentRound = new DebateRound(round + 1);

        return Flux.fromIterable(request.getAgentIds())
                .flatMap(agentId -> {
                    String context = buildDebateContext(request.getTask(), debateRounds, agentId);
                    return executeAgent(agentId, context, request)
                            .doOnSuccess(response -> currentRound.addArgument(agentId, response));
                })
                .collectList()
                .flatMap(responses -> {
                    debateRounds.add(currentRound);
                    return executeDebate(request, debateRounds, round + 1);
                });
    }

    /**
     * 构建带上下文的任务
     */
    private String buildContextualTask(String originalTask, List<AgentResponse> previousResponses) {
        if (previousResponses.isEmpty()) {
            return originalTask;
        }

        StringBuilder context = new StringBuilder();
        context.append("原始任务：").append(originalTask).append("\n\n");
        context.append("前序 Agent 的工作结果：\n");

        for (int i = 0; i < previousResponses.size(); i++) {
            AgentResponse response = previousResponses.get(i);
            context.append(String.format(
                    "Agent %d (%s)：\n%s\n\n",
                    i + 1, response.getAgentId(), response.getMessage().getContent()));
        }

        context.append("请基于以上信息继续完成任务。");
        return context.toString();
    }

    /**
     * 构建辩论上下文
     */
    private String buildDebateContext(String topic, List<DebateRound> previousRounds, String agentId) {
        StringBuilder context = new StringBuilder();
        context.append("辩论主题：").append(topic).append("\n\n");

        if (!previousRounds.isEmpty()) {
            context.append("之前的辩论轮次：\n");
            for (DebateRound round : previousRounds) {
                context.append(String.format("第 %d 轮：\n", round.getRoundNumber()));
                round.getArguments().forEach((id, response) -> {
                    context.append(String.format(
                            "  %s: %s\n", id, response.getMessage().getContent()));
                });
            }
        }

        context.append("\n请就该主题发表你的观点或回应。");
        return context.toString();
    }

    /**
     * 构建辩论历史
     */
    private String buildDebateHistory(List<DebateRound> rounds) {
        return rounds.stream()
                .map(round -> String.format(
                        "第 %d 轮：\n%s",
                        round.getRoundNumber(),
                        round.getArguments().entrySet().stream()
                                .map(e -> String.format(
                                        "  %s: %s",
                                        e.getKey(), e.getValue().getMessage().getContent()))
                                .collect(Collectors.joining("\n"))))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 合成多个响应
     */
    private String synthesizeResponses(List<AgentResponse> responses) {
        return responses.stream()
                .map(r ->
                        String.format("[%s]: %s", r.getAgentId(), r.getMessage().getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 辩论轮次
     */
    @lombok.Data
    public static class DebateRound {
        private final int roundNumber;
        private final Map<String, AgentResponse> arguments = new HashMap<>();

        public DebateRound(int roundNumber) {
            this.roundNumber = roundNumber;
        }

        public void addArgument(String agentId, AgentResponse response) {
            arguments.put(agentId, response);
        }
    }

    /**
     * 协作类型
     */
    public enum CollaborationType {
        SEQUENTIAL, // 顺序
        PARALLEL, // 并行
        DEBATE, // 辩论
        HIERARCHICAL // 层级
    }
}
