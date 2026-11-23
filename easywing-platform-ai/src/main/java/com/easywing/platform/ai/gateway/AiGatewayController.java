package com.easywing.platform.ai.gateway;

import com.easywing.platform.ai.agent.AgentExecutor;
import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.agent.AgentStreamEvent;
import com.easywing.platform.ai.agent.TaskDecompositionAgent;
import com.easywing.platform.ai.agentdef.AgentCaller;
import com.easywing.platform.ai.artifact.Artifact;
import com.easywing.platform.ai.artifact.ArtifactService;
import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.debug.AgentTrace;
import com.easywing.platform.ai.debug.DebugService;
import com.easywing.platform.ai.image.ImageGenerationService;
import com.easywing.platform.ai.mcp.McpClient;
import com.easywing.platform.ai.mcp.McpResponse;
import com.easywing.platform.ai.mcp.McpServerConfig;
import com.easywing.platform.ai.mcp.McpToolAdapterService;
import com.easywing.platform.ai.memory.MemoryManager;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.multiagent.MultiAgentRequest;
import com.easywing.platform.ai.multiagent.MultiAgentResponse;
import com.easywing.platform.ai.multimodal.MultiModalService;
import com.easywing.platform.ai.prompt.PromptDefinition;
import com.easywing.platform.ai.prompt.PromptRenderRequest;
import com.easywing.platform.ai.prompt.PromptRequest;
import com.easywing.platform.ai.prompt.PromptService;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.easywing.platform.ai.rag.DocumentRecord;
import com.easywing.platform.ai.rag.FileDocumentService;
import com.easywing.platform.ai.rag.RagService;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox;
import com.easywing.platform.ai.search.WebSearchPipeline;
import com.easywing.platform.ai.search.WebSearchRequest;
import com.easywing.platform.ai.search.WebSearchResponse;
import com.easywing.platform.ai.session.BranchDiff;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionExportService;
import com.easywing.platform.ai.session.SessionForkService;
import com.easywing.platform.ai.session.SessionManager;
import com.easywing.platform.ai.session.SessionSearchHit;
import com.easywing.platform.ai.session.SessionSearchService;
import com.easywing.platform.ai.session.ShareLink;
import com.easywing.platform.ai.session.ShareLinkService;
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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
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
    private final SessionForkService sessionForkService;
    private final com.easywing.platform.ai.skill.SkillExecutionService skillExecutionService;
    private final com.easywing.platform.ai.tool.ToolExecutionCache toolExecutionCache;
    private final com.easywing.platform.ai.agent.StreamingReactAgent streamingReactAgent;
    private final com.easywing.platform.ai.tool.ParallelToolExecutor parallelToolExecutor;
    private final com.easywing.platform.ai.rag.RagService ragService;
    private final FileDocumentService fileDocumentService;
    private final com.easywing.platform.ai.multiagent.MultiAgentService multiAgentService;
    private final com.easywing.platform.ai.debug.DebugService debugService;
    private final McpClient mcpClient;
    private final McpToolAdapterService mcpToolAdapterService;
    private final AiProperties aiProperties;
    private final TaskDecompositionAgent taskDecompositionAgent;
    private final com.easywing.platform.ai.observability.UsageLedger usageLedger;
    private final WebSearchPipeline webSearchPipeline;
    private final ArtifactService artifactService;
    private final SessionSearchService sessionSearchService;
    private final SessionExportService sessionExportService;
    private final PromptService promptService;
    private final com.easywing.platform.ai.observability.ContextUsageService contextUsageService;
    private final com.easywing.platform.ai.compression.SessionCompactionService sessionCompactionService;
    private final ObjectProvider<CodeExecutionSandbox> codeExecutionSandbox;
    private final MultiModalService multiModalService;
    private final ImageGenerationService imageGenerationService;
    private final com.easywing.platform.ai.session.ShareLinkService shareLinkService;
    private final com.easywing.platform.ai.agentdef.AgentDefinitionService agentDefinitionService;

    public AiGatewayController(
            AiProviderRegistry providerRegistry,
            AgentExecutor agentExecutor,
            ToolExecutor toolExecutor,
            SkillRegistry skillRegistry,
            MemoryManager memoryManager,
            SessionManager sessionManager,
            SessionForkService sessionForkService,
            com.easywing.platform.ai.skill.SkillExecutionService skillExecutionService,
            com.easywing.platform.ai.tool.ToolExecutionCache toolExecutionCache,
            com.easywing.platform.ai.agent.StreamingReactAgent streamingReactAgent,
            com.easywing.platform.ai.tool.ParallelToolExecutor parallelToolExecutor,
            com.easywing.platform.ai.rag.RagService ragService,
            FileDocumentService fileDocumentService,
            com.easywing.platform.ai.multiagent.MultiAgentService multiAgentService,
            com.easywing.platform.ai.debug.DebugService debugService,
            McpClient mcpClient,
            McpToolAdapterService mcpToolAdapterService,
            AiProperties aiProperties,
            TaskDecompositionAgent taskDecompositionAgent,
            com.easywing.platform.ai.observability.UsageLedger usageLedger,
            WebSearchPipeline webSearchPipeline,
            ArtifactService artifactService,
            SessionSearchService sessionSearchService,
            SessionExportService sessionExportService,
            PromptService promptService,
            com.easywing.platform.ai.observability.ContextUsageService contextUsageService,
            com.easywing.platform.ai.compression.SessionCompactionService sessionCompactionService,
            ObjectProvider<CodeExecutionSandbox> codeExecutionSandbox,
            MultiModalService multiModalService,
            ImageGenerationService imageGenerationService,
            com.easywing.platform.ai.session.ShareLinkService shareLinkService,
            com.easywing.platform.ai.agentdef.AgentDefinitionService agentDefinitionService) {
        this.providerRegistry = providerRegistry;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.skillRegistry = skillRegistry;
        this.memoryManager = memoryManager;
        this.sessionManager = sessionManager;
        this.sessionForkService = sessionForkService;
        this.skillExecutionService = skillExecutionService;
        this.toolExecutionCache = toolExecutionCache;
        this.streamingReactAgent = streamingReactAgent;
        this.parallelToolExecutor = parallelToolExecutor;
        this.ragService = ragService;
        this.fileDocumentService = fileDocumentService;
        this.multiAgentService = multiAgentService;
        this.debugService = debugService;
        this.mcpClient = mcpClient;
        this.mcpToolAdapterService = mcpToolAdapterService;
        this.aiProperties = aiProperties;
        this.taskDecompositionAgent = taskDecompositionAgent;
        this.usageLedger = usageLedger;
        this.webSearchPipeline = webSearchPipeline;
        this.artifactService = artifactService;
        this.sessionSearchService = sessionSearchService;
        this.sessionExportService = sessionExportService;
        this.promptService = promptService;
        this.contextUsageService = contextUsageService;
        this.sessionCompactionService = sessionCompactionService;
        this.codeExecutionSandbox = codeExecutionSandbox;
        this.multiModalService = multiModalService;
        this.imageGenerationService = imageGenerationService;
        this.shareLinkService = shareLinkService;
        this.agentDefinitionService = agentDefinitionService;
    }

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<ChatCompletionResponse>> chatCompletions(
            @Valid @RequestBody ChatCompletionRequest request, @AuthenticationPrincipal Jwt jwt) {
        applyIdentity(request, jwt);
        applyAlwaysOnSkills(request);
        log.info("Chat completions request: model={}", request.getModel());
        return providerRegistry.chat(request).map(ResponseEntity::ok).onErrorResume(e -> {
            log.error("Chat completion failed", e);
            ChatCompletionResponse errorResponse = ChatCompletionResponse.builder()
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content("模型调用失败，请检查模型配置后重试")
                                    .build())
                            .finishReason("error")
                            .build()))
                    .build();
            return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
        });
    }

    @PostMapping(
            value = "/chat/completions/stream",
            produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatCompletionResponse>> streamChatCompletions(
            @Valid @RequestBody ChatCompletionRequest request, @AuthenticationPrincipal Jwt jwt) {
        applyIdentity(request, jwt);
        applyAlwaysOnSkills(request);
        request.setStream(true);
        return providerRegistry
                .streamChat(request)
                .map(response -> ServerSentEvent.<ChatCompletionResponse>builder(response)
                        .event("message")
                        .build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<ChatCompletionResponse>builder()
                        .event("error")
                        .data(ChatCompletionResponse.builder()
                                .choices(List.of(ChatCompletionResponse.Choice.builder()
                                        .message(ChatMessage.builder()
                                                .role("assistant")
                                                .content("生成失败，请稍后重试")
                                                .build())
                                        .finishReason("error")
                                        .build()))
                                .build())
                        .build()));
    }

    private void applyIdentity(ChatCompletionRequest request, Jwt jwt) {
        request.setUserId(requiredUserId(jwt));
        request.setTenantId(tenantId(jwt));
    }

    private void applyAlwaysOnSkills(ChatCompletionRequest request) {
        List<String> instructions = skillRegistry.getAlwaysOnSkills().stream()
                .map(AgentSkill::getPromptTemplate)
                .filter(template -> template != null && !template.isBlank())
                .toList();
        if (instructions.isEmpty()) {
            return;
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessage.ROLE_SYSTEM)
                .content(String.join("\n\n", instructions))
                .build());
        if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }
        request.setMessages(messages);
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

    @PostMapping("/custom-endpoints")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> addCustomEndpoint(
            @Valid @RequestBody CustomEndpointRequest request) {
        URI endpointUri;
        try {
            endpointUri = URI.create(request.getBaseUrl());
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Invalid endpoint URL")));
        }
        if (!("http".equalsIgnoreCase(endpointUri.getScheme()) || "https".equalsIgnoreCase(endpointUri.getScheme()))
                || endpointUri.getHost() == null
                || endpointUri.getUserInfo() != null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Invalid endpoint URL")));
        }
        AiProperties.CustomEndpointConfig endpoint = new AiProperties.CustomEndpointConfig();
        endpoint.setEnabled(true);
        endpoint.setName(request.getName());
        endpoint.setBaseUrl(request.getBaseUrl().replaceAll("/+$", ""));
        endpoint.setApiKey(request.getApiKey());
        endpoint.setModels(request.getModels());
        try {
            providerRegistry.registerCustomEndpoint(request.getId(), endpoint);
            return Mono.just(ResponseEntity.ok(Map.of(
                    "id", request.getId(),
                    "provider", "custom-" + request.getId(),
                    "name", request.getName() == null ? request.getId() : request.getName(),
                    "baseUrl", endpoint.getBaseUrl(),
                    "models", endpoint.getModels())));
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())));
        }
    }

    @GetMapping("/custom-endpoints")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, AiProperties.CustomEndpointConfig>>> listCustomEndpoints() {
        return Mono.just(ResponseEntity.ok(providerRegistry.getCustomEndpoints()));
    }

    @DeleteMapping("/custom-endpoints/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> removeCustomEndpoint(@PathVariable String id) {
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        providerRegistry.removeCustomEndpoint(id);
        return Mono.just(ResponseEntity.noContent().build());
    }

    @GetMapping("/mcp/servers")
    public Mono<ResponseEntity<List<String>>> listMcpServers(@AuthenticationPrincipal Jwt jwt) {
        List<String> servers = aiProperties.getMcp().getServers().keySet().stream()
                .filter(name -> isMcpServerVisible(name, jwt))
                .sorted()
                .toList();
        return Mono.just(ResponseEntity.ok(servers));
    }

    @GetMapping("/mcp/servers/{serverName}/tools")
    public Mono<ResponseEntity<McpResponse>> listMcpTools(
            @PathVariable String serverName, @AuthenticationPrincipal Jwt jwt) {
        McpServerConfig server = requireAuthorizedMcpServer(serverName, jwt);
        return mcpToolAdapterService.listToolsAndRegister(serverName, server).map(this::mcpResponse);
    }

    @PostMapping("/mcp/servers/{serverName}/tools/{toolName}")
    public Mono<ResponseEntity<McpResponse>> callMcpTool(
            @PathVariable String serverName,
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments,
            @AuthenticationPrincipal Jwt jwt) {
        McpServerConfig server = requireAuthorizedMcpServer(serverName, jwt);
        return mcpClient.callTool(server, toolName, arguments).map(this::mcpResponse);
    }

    /**
     * 解析 MCP server 并校验启用状态与租户/角色授权，未通过时抛出 404/403。
     */
    private McpServerConfig requireAuthorizedMcpServer(String serverName, Jwt jwt) {
        McpServerConfig server = aiProperties.getMcp().resolve(serverName);
        if (server == null || !server.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server 不存在或未启用");
        }
        if (server.hasAllowedTenants() && !server.getAllowedTenants().contains(tenantId(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前租户无权访问该 MCP server");
        }
        if (server.hasAllowedRoles() && !hasAnyRole(jwt, server.getAllowedRoles())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权访问该 MCP server");
        }
        return server;
    }

    private boolean isMcpServerVisible(String serverName, Jwt jwt) {
        McpServerConfig server = aiProperties.getMcp().resolve(serverName);
        if (server == null || !server.isEnabled()) {
            return false;
        }
        if (server.hasAllowedTenants() && !server.getAllowedTenants().contains(tenantId(jwt))) {
            return false;
        }
        return !server.hasAllowedRoles() || hasAnyRole(jwt, server.getAllowedRoles());
    }

    private boolean hasAnyRole(Jwt jwt, List<String> requiredRoles) {
        if (jwt == null) {
            return false;
        }
        for (String claimName : List.of("roles", "authorities", "scope")) {
            Object raw = jwt.getClaim(claimName);
            List<String> roles =
                    switch (raw) {
                        case null -> List.of();
                        case String value -> value.isBlank() ? List.of() : List.of(value.split(" "));
                        case java.util.Collection<?> values -> values.stream()
                                .map(String::valueOf)
                                .toList();
                        default -> List.of();
                    };
            if (roles.stream().anyMatch(requiredRoles::contains)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<McpResponse> mcpResponse(McpResponse response) {
        return response.getError() == null
                ? ResponseEntity.ok(response)
                : ResponseEntity.unprocessableEntity().body(response);
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
    public Mono<ResponseEntity<ChatCompletionResponse>> createMessage(
            @Valid @RequestBody MessageRequest request, @AuthenticationPrincipal Jwt jwt) {
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(request.getModel())
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .userId(requiredUserId(jwt))
                .tenantId(tenantId(jwt))
                .build();
        return chatCompletions(completionRequest, jwt);
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
    public Mono<ResponseEntity<AgentResponse>> executeAgent(
            @Valid @RequestBody AgentRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request.getUserId() == null) {
            request.setUserId(requiredUserId(jwt));
        }
        request.setTenantId(tenantId(jwt));
        applyAlwaysOnSkills(request);
        log.info(
                "Agent execute request: agentId={}, prompt={}, useTools={}",
                request.getAgentId(),
                request.getPrompt(),
                request.getConfig() != null && request.getConfig().getMaxToolCalls() > 0);

        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }

        return agentDefinitionService
                .apply(agentCaller(jwt), request)
                .flatMap(agentExecutor::execute)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
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

    @PostMapping("/subagents/delegate")
    public Mono<ResponseEntity<TaskDecompositionAgent.TaskDecompositionResult>> delegateSubtasks(
            @Valid @RequestBody AgentRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request.getUserId() == null) {
            request.setUserId(requiredUserId(jwt));
        }
        request.setTenantId(tenantId(jwt));
        applyAlwaysOnSkills(request);
        return agentDefinitionService
                .apply(agentCaller(jwt), request)
                .flatMap(taskDecompositionAgent::executeWithDecomposition)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/agent/execute/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<AgentStreamEvent>> executeAgentStream(
            @RequestParam @NotBlank String prompt,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "10") @Min(1) int maxIterations,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Streaming agent execute request: prompt={}", prompt);

        AgentRequest request = AgentRequest.builder()
                .agentId(agentId != null ? agentId : "default")
                .userId(requiredUserId(jwt))
                .tenantId(tenantId(jwt))
                .prompt(prompt)
                .config(AgentRequest.AgentConfig.builder()
                        .model(model != null ? model : "gpt-4o")
                        .maxIterations(maxIterations)
                        .maxToolCalls(20)
                        .temperature(0.7)
                        .userId(requiredUserId(jwt))
                        .tenantId(tenantId(jwt))
                        .build())
                .build();
        applyAlwaysOnSkills(request);

        return agentDefinitionService
                .apply(agentCaller(jwt), request)
                .flatMapMany(applied -> streamingReactAgent
                        .executeStream(applied)
                        .map(event -> org.springframework.http.codec.ServerSentEvent.<AgentStreamEvent>builder()
                                .event(event.getType().name())
                                .data(event)
                                .build()))
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
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<AgentSkill>> registerSkill(@Valid @RequestBody AgentSkill skill) {
        if (skill.getTriggerMode() == null || skill.getTriggerMode().isBlank()) {
            skill.setTriggerMode("MANUAL");
        }
        if (!List.of("MANUAL", "AUTO", "ALWAYS_ON")
                .contains(skill.getTriggerMode().toUpperCase())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        skillRegistry.register(skill);
        return Mono.just(ResponseEntity.ok(skill));
    }

    @DeleteMapping("/skills/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> unregisterSkill(@PathVariable String name) {
        skillRegistry.unregister(name);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PostMapping("/skills/{skillName}/execute")
    public Mono<ResponseEntity<SkillExecuteResponse>> executeSkill(
            @PathVariable String skillName,
            @Valid @RequestBody SkillExecuteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Executing skill: {}", skillName);
        request.setUserId(requiredUserId(jwt));
        request.setTenantId(tenantId(jwt));

        SkillContext context = SkillContext.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .input(request.getInput())
                .parameters(request.getParameters())
                .build();
        return executeSkillRequest(skillName, request, context);
    }

    @PostMapping("/skills/auto/execute")
    public Mono<ResponseEntity<SkillExecuteResponse>> executeAutomaticSkill(
            @Valid @RequestBody SkillExecuteRequest request, @AuthenticationPrincipal Jwt jwt) {
        request.setUserId(requiredUserId(jwt));
        request.setTenantId(tenantId(jwt));
        AgentSkill skill = skillRegistry.getAutomaticSkills().stream()
                .filter(candidate ->
                        skillExecutionService.recommendSkills(request.getInput(), Integer.MAX_VALUE).stream()
                                .anyMatch(recommended -> recommended.getName().equals(candidate.getName())))
                .findFirst()
                .orElse(null);
        if (skill == null) {
            return Mono.just(ResponseEntity.<SkillExecuteResponse>noContent().build());
        }
        SkillContext context = SkillContext.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .input(request.getInput())
                .parameters(request.getParameters())
                .build();
        return executeSkillRequest(skill.getName(), request, context);
    }

    private Mono<ResponseEntity<SkillExecuteResponse>> executeSkillRequest(
            String skillName, SkillExecuteRequest request, SkillContext context) {
        AgentSkill skill = skillRegistry.get(skillName);
        if (skill != null
                && skill.getExecutor() == null
                && skill.getPromptTemplate() != null
                && !skill.getPromptTemplate().isBlank()) {
            String model = request.getModel();
            if (model == null || model.isBlank()) {
                model = providerRegistry
                        .getDefaultProvider()
                        .flatMap(provider ->
                                provider.getSupportedModels().stream().findFirst())
                        .map(ModelInfo::getId)
                        .orElse(null);
            }
            if (model == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new SkillExecuteResponse(false, null, null, "当前没有可用模型")));
            }
            String instruction = skill.getPromptTemplate().replace("{{input}}", request.getInput());
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(List.of(
                            ChatMessage.builder()
                                    .role(ChatMessage.ROLE_SYSTEM)
                                    .content(instruction)
                                    .build(),
                            ChatMessage.builder()
                                    .role(ChatMessage.ROLE_USER)
                                    .content(request.getInput())
                                    .build()))
                    .userId(request.getUserId())
                    .tenantId(request.getTenantId())
                    .build();
            return providerRegistry
                    .chat(completionRequest)
                    .map(response -> ResponseEntity.ok(new SkillExecuteResponse(
                            true, response.getChoices().get(0).getMessage().getContent(), "技能执行成功", null)))
                    .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
                            .body(new SkillExecuteResponse(false, null, null, "技能执行失败"))));
        }
        return skillExecutionService
                .executeSkill(skillName, context)
                .map(result -> ResponseEntity.ok(new SkillExecuteResponse(
                        result.isSuccess(), result.getData(), result.getMessage(), result.getError())))
                .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
                        .body(new SkillExecuteResponse(false, null, null, "技能执行失败"))));
    }

    private void applyAlwaysOnSkills(AgentRequest request) {
        String instructions = skillRegistry.getAlwaysOnSkills().stream()
                .map(AgentSkill::getPromptTemplate)
                .filter(template -> template != null && !template.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        if (!instructions.isBlank()) {
            request.setPrompt(instructions + "\n\n任务：\n" + request.getPrompt());
        }
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
    public Mono<ResponseEntity<RagService.RagResponse>> ragQuery(
            @Valid @RequestBody RagService.RagRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            if (request.getUserId() == null) {
                request.setUserId(jwt.getSubject());
            }
            if (request.getTenantId() == null) {
                request.setTenantId(tenantId(jwt));
            }
        }
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

    @PostMapping("/rag/documents/upload")
    public Mono<ResponseEntity<DocumentRecord>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal Jwt jwt) {
        return fileDocumentService
                .upload(requiredUserId(jwt), tenantId(jwt), file, title)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/rag/documents")
    public Mono<ResponseEntity<List<DocumentRecord>>> listDocuments(@AuthenticationPrincipal Jwt jwt) {
        return fileDocumentService.list(requiredUserId(jwt), tenantId(jwt)).map(ResponseEntity::ok);
    }

    @GetMapping("/rag/documents/{documentId}")
    public Mono<ResponseEntity<DocumentRecord>> getDocument(
            @PathVariable String documentId, @AuthenticationPrincipal Jwt jwt) {
        return fileDocumentService
                .get(documentId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/rag/documents/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable String documentId, @AuthenticationPrincipal Jwt jwt) {
        return fileDocumentService
                .delete(documentId, requiredUserId(jwt), tenantId(jwt))
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    // ========== Web Search API ==========

    @PostMapping("/search")
    public Mono<ResponseEntity<WebSearchResponse>> search(
            @Valid @RequestBody WebSearchRequest request, @AuthenticationPrincipal Jwt jwt) {
        log.info("Web search: {}", request.getQuery());
        return webSearchPipeline
                .search(request, jwt != null ? jwt.getSubject() : null, jwt != null ? tenantId(jwt) : "default")
                .map(ResponseEntity::ok);
    }

    // ========== Artifacts API ==========

    @GetMapping("/sessions/{sessionId}/artifacts")
    public Mono<ResponseEntity<List<Artifact>>> listArtifacts(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return artifactService
                .listArtifacts(sessionId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}/artifacts/{artifactId}")
    public Mono<ResponseEntity<Artifact>> getArtifact(
            @PathVariable String sessionId, @PathVariable String artifactId, @AuthenticationPrincipal Jwt jwt) {
        return artifactService
                .getArtifact(sessionId, artifactId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}/artifacts/{artifactId}/export")
    public Mono<ResponseEntity<String>> exportArtifact(
            @PathVariable String sessionId,
            @PathVariable String artifactId,
            @RequestParam(defaultValue = "svg") String format,
            @AuthenticationPrincipal Jwt jwt) {
        return artifactService
                .getArtifact(sessionId, artifactId, requiredUserId(jwt), tenantId(jwt))
                .flatMap(artifact -> artifactService.exportMermaid(artifact, format))
                .map(body -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + artifactId + ".svg\"")
                        .header("Content-Type", "image/svg+xml")
                        .body(body));
    }

    // ========== Session Search / Export API ==========

    @GetMapping("/sessions/search")
    public Mono<ResponseEntity<List<SessionSearchHit>>> searchSessions(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return sessionSearchService
                .search(requiredUserId(jwt), tenantId(jwt), query, limit)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}/export")
    public Mono<ResponseEntity<String>> exportSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "json") String format,
            @AuthenticationPrincipal Jwt jwt) {
        return sessionExportService
                .export(sessionId, requiredUserId(jwt), tenantId(jwt), format)
                .map(result -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + result.fileName() + "\"")
                        .header("Content-Type", result.contentType())
                        .body(result.body()));
    }

    // ========== Prompt Library API ==========

    @GetMapping("/prompts")
    public Mono<ResponseEntity<List<PromptDefinition>>> listPrompts(@AuthenticationPrincipal Jwt jwt) {
        return promptService.list(requiredUserId(jwt), tenantId(jwt)).map(ResponseEntity::ok);
    }

    @PostMapping("/prompts")
    public Mono<ResponseEntity<PromptDefinition>> createPrompt(
            @Valid @RequestBody PromptRequest request, @AuthenticationPrincipal Jwt jwt) {
        return promptService
                .create(requiredUserId(jwt), tenantId(jwt), request.title(), request.content(), request.category())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/prompts/{id}")
    public Mono<ResponseEntity<PromptDefinition>> getPrompt(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return promptService.get(requiredUserId(jwt), tenantId(jwt), id).map(ResponseEntity::ok);
    }

    @PutMapping("/prompts/{id}")
    public Mono<ResponseEntity<PromptDefinition>> updatePrompt(
            @PathVariable String id, @Valid @RequestBody PromptRequest request, @AuthenticationPrincipal Jwt jwt) {
        return promptService
                .update(requiredUserId(jwt), tenantId(jwt), id, request.title(), request.content(), request.category())
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/prompts/{id}")
    public Mono<ResponseEntity<Void>> deletePrompt(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return promptService
                .delete(requiredUserId(jwt), tenantId(jwt), id)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/prompts/{id}/render")
    public Mono<ResponseEntity<String>> renderPrompt(
            @PathVariable String id,
            @RequestBody(required = false) PromptRenderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return promptService
                .render(requiredUserId(jwt), tenantId(jwt), id, request == null ? Map.of() : request.variables())
                .map(ResponseEntity::ok);
    }

    // ========== Context Usage API（PRD 4.8） ==========

    @GetMapping("/sessions/{sessionId}/context-usage")
    public Mono<ResponseEntity<com.easywing.platform.ai.observability.ContextUsageService.ContextUsage>>
            getSessionContextUsage(@PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return contextUsageService
                .getSessionUsage(sessionId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/context-usage")
    public Mono<ResponseEntity<com.easywing.platform.ai.observability.ContextUsageService.ContextUsageSummary>>
            getCurrentUserContextUsage(@AuthenticationPrincipal Jwt jwt) {
        return contextUsageService
                .getUserUsage(requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/admin/context-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<com.easywing.platform.ai.observability.ContextUsageService.ContextUsageSummary>>
            getAdminContextUsage() {
        return contextUsageService.getPlatformUsage().map(ResponseEntity::ok);
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
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<List<AgentTrace>>> getAllTraces() {
        return Mono.just(ResponseEntity.ok(debugService.getAllTraces()));
    }

    @GetMapping("/debug/traces/{traceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<AgentTrace>> getTrace(@PathVariable String traceId) {
        return Mono.just(debugService
                .getTrace(traceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build()));
    }

    @GetMapping("/debug/traces/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<List<AgentTrace>>> getRecentTraces(@RequestParam(defaultValue = "10") int limit) {
        return Mono.just(ResponseEntity.ok(debugService.getRecentTraces(limit)));
    }

    @GetMapping("/debug/report/{traceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<DebugService.DebugReport>> getDebugReport(@PathVariable String traceId) {
        DebugService.DebugReport report = debugService.generateReport(traceId);
        return Mono.just(
                report != null
                        ? ResponseEntity.ok(report)
                        : ResponseEntity.notFound().build());
    }

    @DeleteMapping("/debug/traces/{traceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> clearTrace(@PathVariable String traceId) {
        debugService.clearTrace(traceId);
        return Mono.just(ResponseEntity.ok().build());
    }

    @DeleteMapping("/debug/traces")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    public Mono<ResponseEntity<ConversationContext>> getMemory(
            @PathVariable String conversationId, @AuthenticationPrincipal Jwt jwt) {
        return memoryManager.getContext(scopedMemoryId(jwt, conversationId)).map(ResponseEntity::ok);
    }

    @PostMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<ConversationContext>> saveMemory(
            @PathVariable String conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConversationContext context) {
        context.setConversationId(scopedMemoryId(jwt, conversationId));
        return memoryManager.saveContext(context).map(ResponseEntity::ok);
    }

    @DeleteMapping("/memory/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteMemory(
            @PathVariable String conversationId, @AuthenticationPrincipal Jwt jwt) {
        return memoryManager
                .deleteContext(scopedMemoryId(jwt, conversationId))
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/memory/{conversationId}/messages")
    public Mono<ResponseEntity<ConversationContext>> addMemoryMessage(
            @PathVariable String conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatMessage message) {
        return memoryManager
                .addMessage(scopedMemoryId(jwt, conversationId), message)
                .map(ResponseEntity::ok);
    }

    private String scopedMemoryId(Jwt jwt, String conversationId) {
        return tenantId(jwt) + ":" + requiredUserId(jwt) + ":" + conversationId;
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<List<ConversationSession>>> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionManager
                .getUserSessions(requiredUserId(jwt), AgentCaller.tenantIdOf(jwt))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/sessions")
    public Mono<ResponseEntity<ConversationSession>> createSession(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSessionRequest request) {
        return sessionManager
                .createSession(requiredUserId(jwt), request.getSessionName())
                .flatMap(session -> {
                    session.setTenantId(tenantId(jwt));
                    return sessionManager.updateSession(session);
                })
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<ConversationSession>> getSession(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return ownedSession(sessionId, jwt).map(ResponseEntity::ok);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Void>> deleteSession(@PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return ownedSession(sessionId, jwt)
                .then(sessionManager.deleteSession(sessionId))
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PatchMapping("/sessions/{sessionId}/model")
    public Mono<ResponseEntity<ConversationSession>> changeSessionModel(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeModelRequest request) {
        if (!providerRegistry.supportsModel(request.getModel())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        if (request.getProvider() != null
                && providerRegistry
                        .getProvider(request.getProvider())
                        .filter(provider -> provider.supportsModel(request.getModel()))
                        .isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return ownedSession(sessionId, jwt).flatMap(session -> {
            session.setModel(request.getModel());
            session.setProvider(request.getProvider());
            return sessionManager.updateSession(session).map(ResponseEntity::ok);
        });
    }

    @PostMapping("/sessions/{sessionId}/chat")
    public Mono<ResponseEntity<ChatCompletionResponse>> chatInSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatCompletionRequest request) {
        if (request.getProvider() != null
                && providerRegistry
                        .getProvider(request.getProvider())
                        .filter(provider -> provider.supportsModel(request.getModel()))
                        .isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return ownedSession(sessionId, jwt).flatMap(session -> {
            session.setModel(request.getModel());
            session.setProvider(request.getProvider());
            session.setMessages(new ArrayList<>(request.getMessages()));
            applyIdentity(request, jwt);
            applyAlwaysOnSkills(request);
            return providerRegistry.chat(request).flatMap(response -> {
                appendAssistantMessage(session, response);
                return sessionManager.updateSession(session).thenReturn(ResponseEntity.ok(response));
            });
        });
    }

    @PutMapping("/sessions/{sessionId}/messages/{messageIndex}")
    public Mono<ResponseEntity<ChatCompletionResponse>> editAndResubmit(
            @PathVariable String sessionId,
            @PathVariable @Min(0) int messageIndex,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatMessage editedMessage) {
        if (!ChatMessage.ROLE_USER.equals(editedMessage.getRole())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return ownedSession(sessionId, jwt).flatMap(session -> {
            List<ChatMessage> messages =
                    new ArrayList<>(session.getMessages() == null ? List.of() : session.getMessages());
            if (messageIndex >= messages.size()
                    || !ChatMessage.ROLE_USER.equals(messages.get(messageIndex).getRole())) {
                return Mono.just(ResponseEntity.notFound().build());
            }
            messages.set(messageIndex, editedMessage);
            List<ChatMessage> editedMessages = new ArrayList<>(messages.subList(0, messageIndex + 1));
            if (session.getModel() == null || session.getModel().isBlank()) {
                return Mono.just(ResponseEntity.badRequest().build());
            }
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(session.getModel())
                    .provider(session.getProvider())
                    .messages(editedMessages)
                    .userId(session.getUserId())
                    .tenantId(session.getTenantId())
                    .build();
            applyAlwaysOnSkills(request);
            return providerRegistry.chat(request).flatMap(response -> {
                session.setMessages(editedMessages);
                appendAssistantMessage(session, response);
                return sessionManager.updateSession(session).thenReturn(ResponseEntity.ok(response));
            });
        });
    }

    @PostMapping("/sessions/{sessionId}/continue")
    public Mono<ResponseEntity<ChatCompletionResponse>> continueSession(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return ownedSession(sessionId, jwt).flatMap(session -> {
            if (session.getMessages() == null || session.getMessages().isEmpty() || session.getModel() == null) {
                return Mono.just(ResponseEntity.badRequest().build());
            }
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(session.getModel())
                    .provider(session.getProvider())
                    .messages(new ArrayList<>(session.getMessages()))
                    .userId(session.getUserId())
                    .tenantId(session.getTenantId())
                    .build();
            applyAlwaysOnSkills(request);
            return providerRegistry.chat(request).flatMap(response -> {
                appendAssistantMessage(session, response);
                return sessionManager.updateSession(session).thenReturn(ResponseEntity.ok(response));
            });
        });
    }

    @GetMapping("/sandbox/languages")
    public Mono<ResponseEntity<Map<String, Object>>> sandboxLanguages(@AuthenticationPrincipal Jwt jwt) {
        CodeExecutionSandbox sandbox = codeExecutionSandbox.getIfAvailable();
        if (sandbox == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("enabled", false, "languages", List.of())));
        }
        return Mono.just(ResponseEntity.ok(Map.of("enabled", true, "languages", sandbox.getSupportedLanguages())));
    }

    @PostMapping("/sandbox/execute")
    public Mono<ResponseEntity<?>> executeSandbox(
            @Valid @RequestBody SandboxExecuteRequest request, @AuthenticationPrincipal Jwt jwt) {
        CodeExecutionSandbox sandbox = codeExecutionSandbox.getIfAvailable();
        if (sandbox == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "代码沙箱未启用：easywing.ai.sandbox.enabled=false")));
        }
        CodeExecutionSandbox.SandboxConfig config = sandbox.configWithOverrides(
                request.getAllowFileSystemAccess(), request.getAllowNetworkAccess(), request.getMaxExecutionTime());
        return sandbox.executeCode(request.getCode(), request.getLanguage(), request.getInput(), Map.of(), config)
                .onErrorResume(
                        IllegalArgumentException.class,
                        e -> Mono.just(CodeExecutionSandbox.ExecutionResult.builder()
                                .success(false)
                                .error(e.getMessage())
                                .build()))
                .map(AiGatewayController::sandboxResponse);
    }

    /**
     * 带文件上传的沙箱执行（PRD 4.3「文件上传、处理与结果下载」）。
     * <p>
     * multipart 字段：{@code code}、{@code language} 必填；{@code input}、{@code files} 可选；
     * {@code allowFileSystemAccess} / {@code allowNetworkAccess} / {@code maxExecutionTime} 为请求级覆盖。
     * 程序产生或修改的文件以 base64 形式随 {@code outputFiles} 返回，可直接下载。
     */
    @PostMapping(value = "/sandbox/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> executeSandboxWithFiles(
            @RequestParam("code") @NotBlank String code,
            @RequestParam("language") @NotBlank String language,
            @RequestParam(value = "input", required = false) String input,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "allowFileSystemAccess", defaultValue = "false") boolean allowFileSystemAccess,
            @RequestParam(value = "allowNetworkAccess", defaultValue = "false") boolean allowNetworkAccess,
            @RequestParam(value = "maxExecutionTime", required = false) Long maxExecutionTime,
            @AuthenticationPrincipal Jwt jwt) {
        CodeExecutionSandbox sandbox = codeExecutionSandbox.getIfAvailable();
        if (sandbox == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "代码沙箱未启用：easywing.ai.sandbox.enabled=false")));
        }
        Map<String, byte[]> fileMap = new LinkedHashMap<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                try {
                    fileMap.put(file.getOriginalFilename(), file.getBytes());
                } catch (IOException e) {
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", "读取上传文件失败: " + file.getOriginalFilename())));
                }
            }
        }
        CodeExecutionSandbox.SandboxConfig config =
                sandbox.configWithOverrides(allowFileSystemAccess, allowNetworkAccess, maxExecutionTime);
        return sandbox.executeCode(code, language, input, fileMap, config)
                .onErrorResume(
                        IllegalArgumentException.class,
                        e -> Mono.just(CodeExecutionSandbox.ExecutionResult.builder()
                                .success(false)
                                .error(e.getMessage())
                                .build()))
                .map(AiGatewayController::sandboxResponse);
    }

    private static ResponseEntity<?> sandboxResponse(CodeExecutionSandbox.ExecutionResult result) {
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/sessions/{sessionId}/compact")
    public Mono<ResponseEntity<com.easywing.platform.ai.compression.SessionCompactionService.CompactionResult>>
            compactSession(
                    @PathVariable String sessionId,
                    @AuthenticationPrincipal Jwt jwt,
                    @RequestBody(required = false) @Valid CompactSessionRequest request) {
        CompactSessionRequest body = request == null ? new CompactSessionRequest() : request;
        return sessionCompactionService
                .compact(
                        sessionId,
                        requiredUserId(jwt),
                        tenantId(jwt),
                        body.getTargetTokens(),
                        body.getKeepLastMessages())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/sessions/{sessionId}/fork")
    public Mono<ResponseEntity<ConversationSession>> forkSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) @Valid ForkSessionRequest request) {
        ForkSessionRequest body = request == null ? new ForkSessionRequest() : request;
        return sessionForkService
                .fork(sessionId, requiredUserId(jwt), tenantId(jwt), body.getForkPointIndex(), body.getName())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}/branches")
    public Mono<ResponseEntity<List<ConversationSession>>> sessionBranches(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return sessionForkService
                .branches(sessionId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sessions/{sessionId}/diff")
    public Mono<ResponseEntity<BranchDiff>> sessionDiff(
            @PathVariable String sessionId, @RequestParam String otherSessionId, @AuthenticationPrincipal Jwt jwt) {
        return sessionForkService
                .diff(sessionId, otherSessionId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    // ========== 会话共享链接（PRD 4.7 / 5.2） ==========

    /**
     * 创建共享链接。需 JWT 且会话归属当前用户/租户。
     */
    @PostMapping("/sessions/{sessionId}/share")
    public Mono<ResponseEntity<ShareLinkResponse>> createShare(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) @Valid CreateShareRequest request) {
        CreateShareRequest body = request == null ? new CreateShareRequest() : request;
        return shareLinkService
                .create(sessionId, requiredUserId(jwt), tenantId(jwt), body.getTtlHours(), body.getMaxAccesses())
                .map(link -> ResponseEntity.ok(new ShareLinkResponse(
                        link.getId(),
                        link.getSessionId(),
                        link.getCreatedAt(),
                        link.getExpiresAt(),
                        link.getMaxAccesses())));
    }

    /**
     * 列出当前用户/租户在某会话下的共享链接。
     */
    @GetMapping("/sessions/{sessionId}/shares")
    public Mono<ResponseEntity<List<ShareLink>>> listShares(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return shareLinkService
                .listBySession(sessionId, requiredUserId(jwt), tenantId(jwt))
                .map(ResponseEntity::ok);
    }

    /**
     * 撤销共享链接。仅创建者可撤销。
     */
    @DeleteMapping("/shares/{shareId}")
    public Mono<ResponseEntity<Void>> revokeShare(@PathVariable String shareId, @AuthenticationPrincipal Jwt jwt) {
        return shareLinkService
                .revoke(shareId, requiredUserId(jwt), tenantId(jwt))
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    /**
     * 通过共享令牌读取会话只读视图。公开接口（网关与 AI 服务均需放行），带限流。
     */
    @GetMapping("/shared/{token}")
    public Mono<ResponseEntity<ShareLinkService.SharedSessionView>> getShared(@PathVariable String token) {
        return shareLinkService.getShared(token).map(ResponseEntity::ok);
    }

    private Mono<ConversationSession> ownedSession(String sessionId, Jwt jwt) {
        String userId = requiredUserId(jwt);
        String tenantId = tenantId(jwt);
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> userId.equals(session.getUserId()) && tenantId.equals(session.getTenantId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private String requiredUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return jwt.getSubject();
    }

    private String tenantId(Jwt jwt) {
        return AgentCaller.tenantIdOf(jwt);
    }

    private AgentCaller agentCaller(Jwt jwt) {
        AgentCaller caller = AgentCaller.fromJwt(jwt);
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return caller;
    }

    private void appendAssistantMessage(ConversationSession session, ChatCompletionResponse response) {
        if (response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider returned an empty response");
        }
        List<ChatMessage> messages = new ArrayList<>(session.getMessages() == null ? List.of() : session.getMessages());
        messages.add(response.getChoices().get(0).getMessage());
        session.setMessages(messages);
        session.setMessageCount(messages.size());
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
        private String userId;

        private String sessionName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxExecuteRequest {

        @NotBlank(message = "代码不能为空")
        private String code;

        @NotBlank(message = "语言不能为空")
        private String language;

        /** 标准输入内容，可选 */
        private String input;

        /** 请求级覆盖：是否允许文件系统访问；为空时取 easywing.ai.sandbox.allow-file-system-access */
        private Boolean allowFileSystemAccess;

        /** 请求级覆盖：是否允许网络访问；为空时取 easywing.ai.sandbox.allow-network-access */
        private Boolean allowNetworkAccess;

        /** 请求级覆盖：执行超时（毫秒），必须为正数 */
        @Min(value = 1, message = "maxExecutionTime 必须为正数")
        private Long maxExecutionTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompactSessionRequest {

        /** 目标 token 预算；为空时取 easywing.ai.compression.target-tokens */
        @Min(1)
        private Integer targetTokens;

        /** 完整保留的最近消息条数；为空时取 keep-last-messages */
        @Min(0)
        private Integer keepLastMessages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateShareRequest {

        /** 有效期（小时）；为空时取 easywing.ai.share.default-ttl-hours，0 表示永不过期 */
        @Min(0)
        private Integer ttlHours;

        /** 最大访问次数；为空或 0 表示不限 */
        @Min(0)
        private Integer maxAccesses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareLinkResponse {

        private String shareId;
        private String sessionId;
        private long createdAt;
        private long expiresAt;
        private int maxAccesses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForkSessionRequest {
        private String name;

        @Min(0)
        private Integer forkPointIndex;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeModelRequest {
        @NotBlank
        private String model;

        private String provider;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomEndpointRequest {
        @NotBlank
        private String id;

        private String name;

        @NotBlank
        private String baseUrl;

        private String apiKey;

        @NotEmpty
        private List<@NotBlank String> models;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillExecuteRequest {
        private String userId;
        private String tenantId;
        private String sessionId;
        private String model;

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

    @GetMapping("/usage")
    public Mono<ResponseEntity<com.easywing.platform.ai.observability.UsageLedger.UsageReport>> getCurrentUserUsage(
            @AuthenticationPrincipal Jwt jwt) {
        return Mono.just(ResponseEntity.ok(usageLedger.buildUserReport(requiredUserId(jwt), tenantId(jwt))));
    }

    @GetMapping("/usage/records")
    public Mono<ResponseEntity<List<com.easywing.platform.ai.observability.UsageRecord>>> getCurrentUserUsageRecords(
            @AuthenticationPrincipal Jwt jwt) {
        return Mono.just(ResponseEntity.ok(usageLedger.getUserRecords(requiredUserId(jwt), tenantId(jwt))));
    }

    @GetMapping("/admin/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<com.easywing.platform.ai.observability.UsageLedger.UsageReport>> getAdminUsage() {
        return Mono.just(ResponseEntity.ok(usageLedger.buildReport()));
    }

    @DeleteMapping("/admin/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> clearUsage() {
        usageLedger.clear();
        return Mono.just(ResponseEntity.noContent().build());
    }

    // ========== 多模态与图像 API（PRD 4.6） ==========

    /**
     * 多模态理解：TEXT + IMAGE（URL 或 base64 字节），结果由视觉模型真实返回。
     */
    @PostMapping("/multimodal")
    public Mono<ResponseEntity<MultiModalService.MultiModalResponse>> processMultiModal(
            @Valid @RequestBody MultiModalService.MultiModalInput request, @AuthenticationPrincipal Jwt jwt) {
        if (request.getUserId() == null) {
            request.setUserId(requiredUserId(jwt));
        }
        if (request.getTenantId() == null) {
            request.setTenantId(tenantId(jwt));
        }
        return multiModalService.processMultiModal(request).map(ResponseEntity::ok);
    }

    /**
     * 文生图。图像生成默认关闭，未启用时返回 503。
     */
    @PostMapping("/image/generate")
    public Mono<ResponseEntity<ImageGenerationService.ImageGenerationResponse>> generateImage(
            @Valid @RequestBody ImageGenerationService.ImageGenerationRequest request) {
        return imageGenerationService.generate(request).map(ResponseEntity::ok);
    }

    /**
     * 图生图（multipart 语义，JSON 中以 base64 传 {@code image}）。
     */
    @PostMapping("/image/edit")
    public Mono<ResponseEntity<ImageGenerationService.ImageGenerationResponse>> editImage(
            @Valid @RequestBody ImageGenerationService.ImageEditRequest request) {
        return imageGenerationService.edit(request).map(ResponseEntity::ok);
    }

    /**
     * 当前可用的出图模型清单。
     */
    @GetMapping("/image/models")
    public Mono<ResponseEntity<List<ImageGenerationService.ImageModelInfo>>> imageModels() {
        return Mono.just(ResponseEntity.ok(imageGenerationService.models()));
    }
}
