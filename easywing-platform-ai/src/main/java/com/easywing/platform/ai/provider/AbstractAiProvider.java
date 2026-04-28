package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractAiProvider implements AiProvider {

    protected final WebClient webClient;
    protected final Map<String, String> config;

    protected AbstractAiProvider(WebClient webClient, Map<String, String> config) {
        this.webClient = webClient;
        this.config = config;
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return List.of();
    }

    @Override
    public ModelInfo getModelInfo(String model) {
        return getSupportedModels().stream()
                .filter(m -> m.getId().equals(model))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Mono<Boolean> validateConnection() {
        return Mono.just(true);
    }

    @Override
    public Function<ChatMessage, Mono<ChatMessage>> getThinkingEnhancer() {
        return msg -> Mono.just(msg);
    }

    protected abstract String getApiBaseUrl();

    protected abstract String getApiKey();

    protected abstract Set<String> getSupportedModelIds();

    protected WebClient getWebClient() {
        return webClient;
    }

    public static class OpenAiModels {
        public static final String GPT_4O = "gpt-4o";
        public static final String GPT_4O_MINI = "gpt-4o-mini";
        public static final String GPT_4_TURBO = "gpt-4-turbo";
        public static final String GPT_4 = "gpt-4";
        public static final String GPT_35_TURBO = "gpt-3.5-turbo";
    }

    public static class AnthropicModels {
        public static final String CLAUDE_3_5_SONNET = "claude-3-5-sonnet-20241022";
        public static final String CLAUDE_3_OPUS = "claude-3-opus-20240229";
        public static final String CLAUDE_3_SONNET = "claude-3-sonnet-20240229";
        public static final String CLAUDE_3_HAiku = "claude-3-haiku-20240307";
    }

    public static class GoogleModels {
        public static final String GEMINI_2_FLASH = "gemini-2.0-flash-exp";
        public static final String GEMINI_1_5_PRO = "gemini-1.5-pro";
        public static final String GEMINI_1_5_FLASH = "gemini-1.5-flash";
    }
}