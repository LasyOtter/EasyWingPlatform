package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

public interface AiProvider {

    String getProviderName();

    boolean supportsModel(String model);

    List<ModelInfo> getSupportedModels();

    ModelInfo getModelInfo(String model);

    Mono<ChatCompletionResponse> chat(ChatCompletionRequest request);

    Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request);

    Mono<Boolean> validateConnection();

    default Function<ChatMessage, Mono<ChatMessage>> getThinkingEnhancer() {
        return msg -> Mono.just(msg);
    }

    default Mono<ChatCompletionResponse> chatWithTools(ChatCompletionRequest request) {
        return chat(request);
    }

    default Flux<ChatCompletionResponse> streamChatWithTools(ChatCompletionRequest request) {
        return streamChat(request);
    }
}