package com.easywing.platform.ai.gateway;

import com.easywing.platform.ai.agent.AgentExecutor;
import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.agent.AgentStreamEvent;
import com.easywing.platform.ai.debug.AgentTrace;
import com.easywing.platform.ai.debug.DebugService;
import com.easywing.platform.ai.memory.MemoryManager;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.multiagent.MultiAgentRequest;
import com.easywing.platform.ai.multiagent.MultiAgentResponse;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.rag.RagService;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import com.easywing.platform.ai.skill.AgentSkill;
import com.easywing.platform.ai.skill.SkillContext;
import com.easywing.platform.ai.skill.SkillRegistry;
import com.easywing.platform.ai.tool.ParallelToolExecutor;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/v1/ai")
@ApiVersion("v1")
public class AiGatewayController {

    private final AiProviderRegistry providerRegistry;
    private final AgentExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final SkillRegistry skillRegistry;
    private final MemoryManager memoryManager;
    private final SessionManager sessionManager;
    private final com.easywing.platform.ai.skill.SkillExecutionService skillExecutionService;
    private final com.easywing.platform.ai.tool.ToolExecutionCache toolExecutionCache;
    private final com.easywing.platform.ai.agent.StreamingReactAgent streamingReactAgent;
    private final com.easywing.platform.ai.tool.ParallelToolExecutor parallelToolExecutor;
    private final com.easywing.platform.ai.rag.RagService ragService;
    private final com.easywing.platform.ai.multiagent.MultiAgentService multiAgentService;
    private final com.easywing.platform.ai.debug.DebugService debugService;

    public AiGatewayController(
            AiProviderRegistry providerRegistry,
            AgentExecutor agentExecutor,
            ToolExecutor toolExecutor,
            SkillRegistry skillRegistry,
            MemoryManager memoryManager,
            SessionManager sessionManager,
            com.easywing.platform.ai.skill.SkillExecutionService skillExecutionService,
            com.easywing.platform.ai.tool.ToolExecutionCache toolExecutionCache,
            com.easywing.platform.ai.agent.StreamingReactAgent streamingReactAgent,
            com.easywing.platform.ai.tool.ParallelToolExecutor parallelToolExecutor,
            com.easywing.platform.ai.rag.RagService ragService,
            com.easywing.platform.ai.multiagent.MultiAgentService multiAgentService,
            com.easywing.platform.ai.debug.DebugService debugService) {
        this.providerRegistry = providerRegistry;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.skillRegistry = skillRegistry;
        this.memoryManager = memoryManager;
        this.sessionManager = sessionManager;
        this.skillExecutionService = skillExecutionService;
        this.toolExecutionCache = toolExecutionCache;
        this.streamingReactAgent = streamingReactAgent;
        this.parallelToolExecutor = parallelToolExecutor;
        this.ragService = ragService;
        this.multiAgentService = multiAgentService;
        this.debugService = debugService;
    }

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<ChatCompletionResponse>> chatCompletions(
            @Valid @RequestBody ChatCompletionRequest request) {
        log.info("Chat completions request: model={}", request.getModel());
        return providerRegistry.chat(request).map(ResponseEntity::ok).onErrorResume(e -> {
            log.error("Chat completion failed", e);
            ChatCompletionResponse errorResponse = ChatCompletionResponse.builder()
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content("Error: " + e.getMessage())
                                    .build())
                            .build()))
                    .build();
            return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
        });
    }

    @GetMapping("/models")
    public Mono<ResponseEntity<List<ModelInfo>>> listModels() {
        List<ModelInfo> models = providerRegistry.getAllSupportedModels();
        return Mono.just(ResponseEntity.ok(models));
    }

    @GetMapping("/providers")
    public Mono<ResponseEntity<List<String>>> listProviders() {
        List<String> providers = providerRegistry.getProviderNames();
        return Mono.just(ResponseEntity.ok(providers));
    }

    @GetMapping("/models/{model}")
    public Mono<ResponseEntity<ModelInfo>> getModel(@PathVariable String model) {
        return Mono.fromCallable(() -> {
            return providerRegistry
                    .getProviderForModel(model)
                    .map(provider -> ResponseEntity.ok(provider.getModelInfo(model)))
                    .orElse(ResponseEntity.notFound().build());
        });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }

    @PostMapping("/messages")
    public Mono<ResponseEntity<ChatCompletionResponse>> createMessage(@Valid @RequestBody MessageRequest request) {
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(request.getModel())
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();
        return chatCompletions(completionRequest);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRequest {
        @NotBlank
        private String model;

        @NotNull @NotEmpty
        @Valid
        private List<ChatMessage> messages;

        private double temperature;
        private int maxTokens;
    }

    @PostMapping("/agent/execute")
    public Mono<ResponseEntity<AgentResponse>> executeAgent(@Valid @RequestBody AgentRequest request) {
        log.info(
                "Agent execute request: agentId={}, prompt={}, useTools={}",
                request.getAgentId(),
                request.getPrompt(),
                request.getConfig() != null && request.getConfig().getMaxToolCalls() > 0);

        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }

        return agentExecutor.execute(request).map(ResponseEntity::ok).onErrorResume(e -> {
            log.error("Agent execution failed", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(AgentResponse.builder()
                            .agentId(request.getAgentId())
                            .sessionId(request.getSessionId())
                            .completed(false)
                            .finishReason("error: " + e.getMessage())
                            .build()));
        });
    }

    @GetMapping(value = "/agent/execute/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<AgentStreamEvent>> executeAgentStream(
            @RequestParam @NotBlank String prompt,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "10") @Min(1) int maxIterations) {

        log.info("Streaming agent execute request: prompt={}", prompt);

        AgentRequest request = AgentRequest.builder()
                .agentId(agentId != null ? agentId : "default")
                .prompt(prompt)
                .config(AgentRequest.AgentConfig.builder()
                        .model(model != null ? model : "gpt-4o")
                        .maxIterations(maxIterations)
                        .maxToolCalls(20)
                        .temperature(0.7)
                        .build())
                .build();

        return streamingReactAgent
                .executeStream(request)
                .map(event -> org.springframework.http.codec.ServerSentEvent.<AgentStreamEvent>builder()
                        .event(event.getType().name())
                        .data(event)
                        .build())
                .onErrorResume(e -> {
                    log.error("Streaming execution failed", e);
                    return Flux.just(org.springframework.http.codec.ServerSentEvent.<AgentStreamEvent>builder()
                            .event("ERROR")
                            .data(AgentStreamEvent.error(e.getMessage()))
                            .build());
                });
    }

    @GetMapping("/agent/config")
    public Mono<ResponseEntity<AgentConfigResponse>> getAgentConfig() {
        AgentConfigResponse config = new AgentConfigResponse();
        config.setAvailableModels(providerRegistry.getAllSupportedModels());
        config.setAvailableTools(toolExecutor.getAvailableTools());
        config.setAvailableSkills(skillRegistry.getAll());
        return Mono.just(ResponseEntity.ok(config));
    }

    @GetMapping("/skills")
    public Mono<ResponseEntity<List<AgentSkill>>> listSkills(@RequestParam(required = false) String category) {
        List<AgentSkill> skills;
        if (category != null && !category.isEmpty()) {
            skills = skillRegistry.getByCategory(category);
        } else {
            skills = skillRegistry.getAll();
        }
        return Mono.just(ResponseEntity.ok(skills));
    }

    @PostMapping("/skills")
    public Mono<ResponseEntity<AgentSkill>> registerSkill(@Valid @RequestBody AgentSkill skill) {
        skillRegistry.register(skill);
        return Mono.just(ResponseEntity.ok(skill));
    }

    @DeleteMapping("/skills/{name}")
    public Mono<ResponseEntity<Void>> unregisterSkill(@PathVariable String name) {
        skillRegistry.unregister(name);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PostMapping("/skills/{skillName}/execute")
    public Mono<ResponseEntity<SkillExecuteResponse>> executeSkill(
            @PathVariable String skillName, @Valid @RequestBody SkillExecuteRequest request) {
        log.info("Executing skill: {}", skillName);

        SkillContext context = SkillContext.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .input(request.getInput())
                .parameters(request.getParameters())
                .build();

        return skillExecutionService
                .executeSkill(skillName, context)
                .map(result -> ResponseEntity.ok(new SkillExecuteResponse(
                        result.isSuccess(), result.getData(), result.getMessage(), result.getError())))
                .onErrorResume(e -> {
                    log.error("Skill execution failed", e);
                    return Mono.just(ResponseEntity.ok(new SkillExecuteResponse(false, null, null, e.getMessage())));
                });
    }

    @PostMapping("/skills/chain")
    public Mono<ResponseEntity<SkillExecuteResponse>> executeSkillChain(@Valid @RequestBody SkillChainRequest request) {
        log.info("Executing skill chain: {}", request.getSkillNames());

        SkillContext context = SkillContext.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .input(request.getInput())
                .parameters(request.getParameters())
                .build();

        return skillExecutionService
                .executeSkillChain(request.getSkillNames(), context)
                .map(result -> ResponseEntity.ok(new SkillExecuteResponse(
                        result.isSuccess(), result.getData(), result.getMessage(), result.getError())))
                .onErrorResume(e -> {
                    log.error("Skill chain execution failed", e);
                    return Mono.just(ResponseEntity.ok(new SkillExecuteResponse(false, null, null, e.getMessage())));
                });
    }

    @GetMapping("/skills/recommend")
    public Mono<ResponseEntity<List<AgentSkill>>> recommendSkills(
            @RequestParam @NotBlank String input, @RequestParam(defaultValue = "5") @Min(1) int limit) {
        List<AgentSkill> recommendations = skillExecutionService.recommendSkills(input, limit);
        return Mono.just(ResponseEntity.ok(recommendations));
    }

    // ========== RAG API ==========

    @PostMapping("/rag/query")
    public Mono<ResponseEntity<RagService.RagResponse>> ragQuery(@Valid @RequestBody RagService.RagRequest request) {
        log.info("RAG query: {}", request.getQuery());
        return ragService.query(request).map(ResponseEntity::ok);
    }

    @PostMapping("/rag/documents")
    public Mono<ResponseEntity<Map<String, Object>>> addDocuments(@Valid @RequestBody AddDocumentsRequest request) {
        List<RagService.Document> documents = request.getDocuments();
        log.info("Adding {} documents to RAG", documents.size());
        return ragService
                .addDocuments(documents)
                .map(ids -> ResponseEntity.ok(Map.of("addedCount", ids.size(), "documentIds", ids)));
    }

    @DeleteMapping("/rag/documents/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(@PathVariable String documentId) {
        return ragService
                .deleteDocument(documentId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    // ========== Multi-Agent API ==========

    @PostMapping("/multiagent/sequential")
    public Mono<ResponseEntity<MultiAgentResponse>> sequentialCollaboration(
            @Valid @RequestBody MultiAgentRequest request) {
        log.info(
                "Sequential collaboration with {} agents", request.getAgentIds().size());
        return multiAgentService.sequentialCollaboration(request).map(ResponseEntity::ok);
    }

    @PostMapping("/multiagent/parallel")
    public Mono<ResponseEntity<MultiAgentResponse>> parallelCollaboration(
            @Valid @RequestBody MultiAgentRequest request) {
        log.info("Parallel collaboration with {} agents", request.getAgentIds().size());
        return multiAgentService.parallelCollaboration(request).map(ResponseEntity::ok);
    }

    @PostMapping("/multiagent/debate")
    public Mono<ResponseEntity<MultiAgentResponse>> debateCollaboration(@Valid @RequestBody MultiAgentRequest request) {
        log.info("Debate collaboration with {} agents", request.getAgentIds().size());
        return multiAgentService.debateCollaboration(request).map(ResponseEntity::ok);
    }

    @PostMapping("/multiagent/hierarchical")
    public Mono<ResponseEntity<MultiAgentResponse>> hierarchicalCollaboration(
            @Valid @RequestBody MultiAgentRequest request) {
        log.info("Hierarchical collaboration");
        return multiAgentService.hierarchicalCollaboration(request).map(ResponseEntity::ok);
    }

    // ========== Debug API ==========

    @GetMapping("/debug/traces")
    public Mono<ResponseEntity<List<AgentTrace>>> getAllTraces() {
        return Mono.just(ResponseEntity.ok(debugService.getAllTraces()));
    }

    @GetMapping("/debug/traces/{traceId}")
    public Mono<ResponseEntity<AgentTrace>> getTrace(@PathVariable String traceId) {
        return Mono.just(debugService
                .getTrace(traceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build()));
    }

    @GetMapping("/debug/traces/recent")
    public Mono<ResponseEntity<List<AgentTrace>>> getRecentTraces(@RequestParam(defaultValue = "10") int limit) {
        return Mono.just(ResponseEntity.ok(debugService.getRecentTraces(limit)));
    }

    @GetMapping("/debug/report/{traceId}")
    public Mono<ResponseEntity<DebugService.DebugReport>> getDebugReport(@PathVariable String traceId) {
        DebugService.DebugReport report = debugService.generateReport(traceId);
        return Mono.just(
                report != null
                        ? ResponseEntity.ok(report)
                        : ResponseEntity.notFound().build());
    }

    @DeleteMapping("/debug/traces/{traceId}")
    public Mono<ResponseEntity<Void>> clearTrace(@PathVariable String traceId) {
        debugService.clearTrace(traceId);
        return Mono.just(ResponseEntity.ok().build());
    }

    @DeleteMapping("/debug/traces")
    public Mono<ResponseEntity<Void>> clearAllTraces() {
        debugService.clearAllTraces();
        return Mono.just(ResponseEntity.ok().build());
    }

    @GetMapping("/tools")
    public Mono<ResponseEntity<List<ToolDefinition>>> listTools() {
        return Mono.just(ResponseEntity.ok(toolExecutor.getAvailableTools()));
    }

    @PostMapping("/tools/execute")
    public Mono<ResponseEntity<ToolExecuteResponse>> executeTool(
            @Valid @RequestBody ToolExecuteRequest request, @RequestParam(defaultValue = "false") boolean useCache) {
        log.info("Tool execute request: toolName={}, useCache={}", request.getToolName(), useCache);

        Mono<Map<String, Object>> execution;
        if (useCache && toolExecutionCache != null) {
            execution = toolExecutionCache.executeCached(request.getToolName(), request.getArguments());
        } else {
            execution = toolExecutor.executeTool(request.getToolName(), request.getArguments());
        }

        return execution
                .map(result -> ResponseEntity.ok(new ToolExecuteResponse(true, result, null)))
                .onErrorResume(e -> Mono.just(ResponseEntity.ok(new ToolExecuteResponse(false, null, e.getMessage()))));
    }

    @DeleteMapping("/tools/cache")
    public Mono<ResponseEntity<Map<String, Object>>> clearToolCache(@RequestParam(required = false) String toolName) {
        if (toolExecutionCache == null) {
            return Mono.just(ResponseEntity.ok(Map.of("message", "Cache not enabled")));
        }

        if (toolName != null) {
            toolExecutionCache.clearCache(toolName);
            return Mono.just(ResponseEntity.ok(Map.of("message", "Cache cleared for tool: " + toolName)));
        } else {
            toolExecutionCache.clearCache();
            return Mono.just(ResponseEntity.ok(Map.of("message", "All cache cleared")));
        }
    }

    @GetMapping("/tools/cache/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getToolCacheStats() {
        if (toolExecutionCache == null) {
            return Mono.just(ResponseEntity.ok(Map.of("message", "Cache not enabled")));
        }
        return Mono.just(ResponseEntity.ok(toolExecutionCache.getCacheStats()));
    }

    @PostMapping("/tools/execute/batch")
    public Mono<ResponseEntity<BatchToolExecuteResponse>> executeBatchTools(
            @Valid @RequestBody BatchToolExecuteRequest request) {
        log.info(
                "Batch tool execute request: {} tools, parallel={}",
                request.getToolCalls().size(),
                request.isParallel());

        long startTime = System.currentTimeMillis();

        Mono<List<ParallelToolExecutor.ToolExecutionResult>> execution;

        if (request.isParallel()) {
            execution = parallelToolExecutor.executeParallel(request.getToolCalls());
        } else {
            // 串行执行
            execution = reactor.core.publisher.Flux.fromIterable(request.getToolCalls())
                    .concatMap(call -> toolExecutor
                            .executeTool(call.getToolName(), call.getArguments())
                            .map(result -> ParallelToolExecutor.ToolExecutionResult.builder()
                                    .toolName(call.getToolName())
                                    .callId(call.getCallId())
                                    .arguments(call.getArguments())
                                    .result(result)
                                    .success(Boolean.TRUE.equals(result.get("success")))
                                    .build()))
                    .collectList();
        }

        return execution
                .map(results -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    long successCount = results.stream()
                            .filter(ParallelToolExecutor.ToolExecutionResult::isSuccess)
                            .count();

                    BatchToolExecuteResponse response = new BatchToolExecuteResponse();
                    response.setResults(results);
                    response.setTotalCount(results.size());
                    response.setSuccessCount((int) successCount);
                    response.setFailureCount(results.size() - (int) successCount);
                    response.setTotalExecutionTimeMs(totalTime);
                    response.setParallel(request.isParallel());

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Batch tool execution failed", e);
                    return Mono.just(ResponseEntity.internalServerError().body(new BatchToolExecuteResponse()));
                });
    }

    @GetMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<ConversationContext>> getMemory(@PathVariable String conversationId) {
        return memoryManager.getContext(conversationId).map(ResponseEntity::ok);
    }

    @PostMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<ConversationContext>> saveMemory(
            @PathVariable String conversationId, @Valid @RequestBody ConversationContext context) {
        context.setConversationId(conversationId);
        return memoryManager.saveContext(context).map(ResponseEntity::ok);
    }

    @DeleteMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteMemory(@PathVariable String conversationId) {
        return memoryManager
                .deleteContext(conversationId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/memory/{conversationId}/messages")
    public Mono<ResponseEntity<ConversationContext>> addMemoryMessage(
            @PathVariable String conversationId, @Valid @RequestBody ChatMessage message) {
        return memoryManager.addMessage(conversationId, message).map(ResponseEntity::ok);
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<List<ConversationSession>>> listSessions(@RequestParam String userId) {
        return sessionManager.getUserSessions(userId).map(ResponseEntity::ok);
    }

    @PostMapping("/sessions")
    public Mono<ResponseEntity<ConversationSession>> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return sessionManager
                .createSession(request.getUserId(), request.getSessionName())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<ConversationSession>> getSession(@PathVariable String sessionId) {
        return sessionManager.getSession(sessionId).map(opt -> opt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Void>> deleteSession(@PathVariable String sessionId) {
        return sessionManager
                .deleteSession(sessionId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentConfigResponse {
        private List<ModelInfo> availableModels;
        private List<ToolDefinition> availableTools;
        private List<AgentSkill> availableSkills;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecuteRequest {
        @NotBlank
        private String toolName;

        private Map<String, Object> arguments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecuteResponse {
        private boolean success;
        private Object result;
        private String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {
        @NotBlank
        private String userId;

        private String sessionName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillExecuteRequest {
        private String userId;
        private String sessionId;

        @NotBlank
        private String input;

        private Map<String, Object> parameters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillExecuteResponse {
        private boolean success;
        private Object data;
        private String message;
        private String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillChainRequest {
        private String userId;
        private String sessionId;

        @NotBlank
        private String input;

        @NotEmpty
        private List<String> skillNames;

        private Map<String, Object> parameters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchToolExecuteRequest {
        @NotEmpty
        @Valid
        private List<ParallelToolExecutor.ToolCall> toolCalls;

        private boolean parallel;
        private int maxConcurrency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddDocumentsRequest {
        @NotNull @NotEmpty
        @Valid
        private List<RagService.Document> documents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchToolExecuteResponse {
        private List<ParallelToolExecutor.ToolExecutionResult> results;
        private int totalCount;
        private int successCount;
        private int failureCount;
        private long totalExecutionTimeMs;
        private boolean parallel;
    }
}
