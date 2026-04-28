package com.easywing.platform.ai.agent;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class AgentExecutor {

    private final AiProviderRegistry providerRegistry;

    public AgentExecutor(AiProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public Mono<AgentResponse> execute(AgentRequest request) {
        String agentId = request.getAgentId() != null ? request.getAgentId() : "default";
        List<ChatMessage> messages = new ArrayList<>(request.getMessages() != null ? request.getMessages() : List.of());

        if (request.getPrompt() != null) {
            messages.add(ChatMessage.builder()
                    .role("user")
                    .content(request.getPrompt())
                    .build());
        }

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(request.getConfig() != null && request.getConfig().getModel() != null ?
                        request.getConfig().getModel() : "gpt-4o")
                .messages(messages)
                .temperature(request.getConfig() != null ? request.getConfig().getTemperature() : 0.7)
                .build();

        String finalAgentId = agentId;
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        return providerRegistry.chat(completionRequest)
                .map(response -> {
                    ChatMessage assistantMessage = null;
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        assistantMessage = response.getChoices().get(0).getMessage();
                    }

                    return AgentResponse.builder()
                            .id(UUID.randomUUID().toString())
                            .agentId(finalAgentId)
                            .sessionId(sessionId)
                            .message(assistantMessage)
                            .messages(messages)
                            .completed(true)
                            .finishReason("completed")
                            .build();
                })
                .doOnError(e -> log.error("Agent execution failed", e));
    }
}