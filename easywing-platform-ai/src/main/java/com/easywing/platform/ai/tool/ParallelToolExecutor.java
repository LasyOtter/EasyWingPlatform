package com.easywing.platform.ai.tool;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 并行工具执行器 - 同时执行多个工具调用
 */
@Slf4j
@Component
public class ParallelToolExecutor {

    private final ToolExecutor toolExecutor;

    public ParallelToolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    /**
     * 并行执行多个工具调用
     */
    public Mono<List<ToolExecutionResult>> executeParallel(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Mono.just(List.of());
        }

        log.info("Executing {} tools in parallel", toolCalls.size());
        long startTime = System.currentTimeMillis();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        return Flux.fromIterable(toolCalls)
                .flatMap(toolCall -> {
                    log.debug("Executing tool in parallel: {}", toolCall.getToolName());
                    return toolExecutor
                            .executeTool(toolCall.getToolName(), toolCall.getArguments())
                            .map(result -> {
                                if (Boolean.TRUE.equals(result.get("success"))) {
                                    successCount.incrementAndGet();
                                } else {
                                    failureCount.incrementAndGet();
                                }
                                return ToolExecutionResult.builder()
                                        .toolName(toolCall.getToolName())
                                        .callId(toolCall.getCallId())
                                        .arguments(toolCall.getArguments())
                                        .result(result)
                                        .success(Boolean.TRUE.equals(result.get("success")))
                                        .executionTimeMs(((Number) result.getOrDefault("executionTime", 0)).longValue())
                                        .build();
                            })
                            .onErrorResume(e -> {
                                failureCount.incrementAndGet();
                                log.error("Tool execution failed: {}", toolCall.getToolName(), e);
                                return Mono.just(ToolExecutionResult.builder()
                                        .toolName(toolCall.getToolName())
                                        .callId(toolCall.getCallId())
                                        .arguments(toolCall.getArguments())
                                        .success(false)
                                        .error(e.getMessage())
                                        .build());
                            });
                })
                .collectList()
                .doOnSuccess(results -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.info(
                            "Parallel execution completed: {} tools, {} succeeded, {} failed, total time: {}ms",
                            toolCalls.size(),
                            successCount.get(),
                            failureCount.get(),
                            totalTime);
                });
    }

    /**
     * 并行执行，带最大并发数限制
     */
    public Mono<List<ToolExecutionResult>> executeParallelWithLimit(List<ToolCall> toolCalls, int maxConcurrency) {

        if (toolCalls == null || toolCalls.isEmpty()) {
            return Mono.just(List.of());
        }

        log.info("Executing {} tools in parallel with max concurrency: {}", toolCalls.size(), maxConcurrency);

        return Flux.fromIterable(toolCalls)
                .flatMap(
                        toolCall -> toolExecutor
                                .executeTool(toolCall.getToolName(), toolCall.getArguments())
                                .map(result -> ToolExecutionResult.builder()
                                        .toolName(toolCall.getToolName())
                                        .callId(toolCall.getCallId())
                                        .arguments(toolCall.getArguments())
                                        .result(result)
                                        .success(Boolean.TRUE.equals(result.get("success")))
                                        .build())
                                .onErrorResume(e -> Mono.just(ToolExecutionResult.builder()
                                        .toolName(toolCall.getToolName())
                                        .callId(toolCall.getCallId())
                                        .success(false)
                                        .error(e.getMessage())
                                        .build())),
                        maxConcurrency // 限制并发数
                        )
                .collectList();
    }

    /**
     * 智能并行执行 - 自动分析依赖关系
     */
    public Mono<List<ToolExecutionResult>> executeSmartParallel(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Mono.just(List.of());
        }

        // 分析工具调用的依赖关系
        DependencyAnalysis analysis = analyzeDependencies(toolCalls);

        List<ToolExecutionResult> allResults = new ArrayList<>();

        // 按批次执行（每批内并行，批次间串行）
        return Flux.fromIterable(analysis.getBatches())
                .concatMap(batch -> {
                    log.info("Executing batch of {} tools", batch.size());
                    return executeParallel(batch).doOnSuccess(allResults::addAll);
                })
                .then(Mono.just(allResults));
    }

    /**
     * 分析工具依赖关系
     */
    private DependencyAnalysis analyzeDependencies(List<ToolCall> toolCalls) {
        // 简单实现：按工具名称分组，相同工具串行执行
        Map<String, List<ToolCall>> grouped = new HashMap<>();
        for (ToolCall call : toolCalls) {
            grouped.computeIfAbsent(call.getToolName(), k -> new ArrayList<>()).add(call);
        }

        List<List<ToolCall>> batches = new ArrayList<>();

        // 如果所有工具都不同，可以全部并行
        if (grouped.size() == toolCalls.size()) {
            batches.add(toolCalls);
        } else {
            // 否则按工具分批
            for (List<ToolCall> calls : grouped.values()) {
                batches.add(calls);
            }
        }

        return new DependencyAnalysis(batches);
    }

    /**
     * 依赖分析结果
     */
    private static class DependencyAnalysis {
        private final List<List<ToolCall>> batches;

        public DependencyAnalysis(List<List<ToolCall>> batches) {
            this.batches = batches;
        }

        public List<List<ToolCall>> getBatches() {
            return batches;
        }
    }

    /**
     * 工具调用定义
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ToolCall {
        private String callId; // 调用 ID（用于追踪）

        @NotBlank
        private String toolName; // 工具名称

        private Map<String, Object> arguments; // 参数
        private List<String> dependsOn; // 依赖的其他调用 ID
    }

    /**
     * 工具执行结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ToolExecutionResult {
        private String callId;
        private String toolName;
        private Map<String, Object> arguments;
        private Map<String, Object> result;
        private boolean success;
        private String error;
        private long executionTimeMs;
    }
}
