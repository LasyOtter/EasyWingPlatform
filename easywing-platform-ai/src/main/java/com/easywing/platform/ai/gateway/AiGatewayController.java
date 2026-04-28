package com.easywing.platform.ai.gateway;

import com.easywing.platform.ai.agent.AgentExecutor;
import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.memory.MemoryManager;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import com.easywing.platform.ai.skill.AgentSkill;
import com.easywing.platform.ai.skill.SkillRegistry;
import com.easywing.platform.ai.tool.ToolDefinition;
import com.easywing.platform.ai.tool.ToolExecutor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiGatewayController {

    private final AiProviderRegistry providerRegistry;
    private final AgentExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final SkillRegistry skillRegistry;
    private final MemoryManager memoryManager;
    private final SessionManager sessionManager;

    public AiGatewayController(
            AiProviderRegistry providerRegistry,
            AgentExecutor agentExecutor,
            ToolExecutor toolExecutor,
            SkillRegistry skillRegistry,
            MemoryManager memoryManager,
            SessionManager sessionManager) {
        this.providerRegistry = providerRegistry;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.skillRegistry = skillRegistry;
        this.memoryManager = memoryManager;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<ChatCompletionResponse>> chatCompletions(
            @RequestBody ChatCompletionRequest request) {
        log.info("Chat completions request: model={}", request.getModel());
        return providerRegistry.chat(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
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
            return providerRegistry.getProviderForModel(model)
                    .map(provider -> ResponseEntity.ok(provider.getModelInfo(model)))
                    .orElse(ResponseEntity.notFound().build());
        });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }

    @PostMapping("/messages")
    public Mono<ResponseEntity<ChatCompletionResponse>> createMessage(
            @RequestBody MessageRequest request) {
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
        private String model;
        private List<ChatMessage> messages;
        private double temperature;
        private int maxTokens;
    }

    @PostMapping("/agent/execute")
    public Mono<ResponseEntity<AgentResponse>> executeAgent(@RequestBody AgentRequest request) {
        log.info("Agent execute request: agentId={}, prompt={}", request.getAgentId(), request.getPrompt());
        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }
        return agentExecutor.execute(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Agent execution failed", e);
                    return Mono.just(ResponseEntity.internalServerError().body(
                            AgentResponse.builder()
                                    .agentId(request.getAgentId())
                                    .sessionId(request.getSessionId())
                                    .completed(false)
                                    .finishReason("error: " + e.getMessage())
                                    .build()));
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
    public Mono<ResponseEntity<List<AgentSkill>>> listSkills(
            @RequestParam(required = false) String category) {
        List<AgentSkill> skills;
        if (category != null && !category.isEmpty()) {
            skills = skillRegistry.getByCategory(category);
        } else {
            skills = skillRegistry.getAll();
        }
        return Mono.just(ResponseEntity.ok(skills));
    }

    @PostMapping("/skills")
    public Mono<ResponseEntity<AgentSkill>> registerSkill(@RequestBody AgentSkill skill) {
        skillRegistry.register(skill);
        return Mono.just(ResponseEntity.ok(skill));
    }

    @DeleteMapping("/skills/{name}")
    public Mono<ResponseEntity<Void>> unregisterSkill(@PathVariable String name) {
        skillRegistry.unregister(name);
        return Mono.just(ResponseEntity.ok().build());
    }

    @GetMapping("/tools")
    public Mono<ResponseEntity<List<ToolDefinition>>> listTools() {
        return Mono.just(ResponseEntity.ok(toolExecutor.getAvailableTools()));
    }

    @PostMapping("/tools/execute")
    public Mono<ResponseEntity<ToolExecuteResponse>> executeTool(@RequestBody ToolExecuteRequest request) {
        log.info("Tool execute request: toolName={}", request.getToolName());
        return toolExecutor.executeTool(request.getToolName(), request.getArguments())
                .map(result -> ResponseEntity.ok(new ToolExecuteResponse(true, result, null)))
                .onErrorResume(e -> Mono.just(ResponseEntity.ok(
                        new ToolExecuteResponse(false, null, e.getMessage()))));
    }

    @GetMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<ConversationContext>> getMemory(@PathVariable String conversationId) {
        return memoryManager.getContext(conversationId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<ConversationContext>> saveMemory(
            @PathVariable String conversationId,
            @RequestBody ConversationContext context) {
        context.setConversationId(conversationId);
        return memoryManager.saveContext(context)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteMemory(@PathVariable String conversationId) {
        return memoryManager.deleteContext(conversationId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/memory/{conversationId}/messages")
    public Mono<ResponseEntity<ConversationContext>> addMemoryMessage(
            @PathVariable String conversationId,
            @RequestBody ChatMessage message) {
        return memoryManager.addMessage(conversationId, message)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<List<ConversationSession>>> listSessions(
            @RequestParam String userId) {
        return sessionManager.getUserSessions(userId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/sessions")
    public Mono<ResponseEntity<ConversationSession>> createSession(@RequestBody CreateSessionRequest request) {
        return sessionManager.createSession(request.getUserId(), request.getSessionName())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<ConversationSession>> getSession(@PathVariable String sessionId) {
        return sessionManager.getSession(sessionId)
                .map(opt -> opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Void>> deleteSession(@PathVariable String sessionId) {
        return sessionManager.deleteSession(sessionId)
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
        private String userId;
        private String sessionName;
    }
}