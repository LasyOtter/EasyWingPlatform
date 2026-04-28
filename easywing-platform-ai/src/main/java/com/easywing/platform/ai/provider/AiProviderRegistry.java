package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ModelInfo;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class AiProviderRegistry {

    private final Map<String, AiProvider> providers = new HashMap<>();
    private final AiProperties properties;
    private String defaultProvider;

    public AiProviderRegistry(List<AiProvider> providers, AiProperties properties) {
        this.properties = properties;

        for (AiProvider provider : providers) {
            if (provider != null) {
                registerProvider(provider);
            }
        }

        String defaultName = properties.getProviders().entrySet().stream()
                .filter(e -> Boolean.TRUE.equals(e.getValue().isEnabled()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse("openai");

        setDefaultProvider(defaultName);

        log.info("AI Provider Registry initialized with providers: {}", this.providers.keySet());
    }

    public void registerProvider(AiProvider provider) {
        providers.put(provider.getProviderName(), provider);
        log.debug("Registered AI provider: {}", provider.getProviderName());
    }

    public void setDefaultProvider(String providerName) {
        if (providers.containsKey(providerName)) {
            this.defaultProvider = providerName;
        } else if (!providers.isEmpty()) {
            this.defaultProvider = providers.keySet().iterator().next();
        }
    }

    public Optional<AiProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public Optional<AiProvider> getDefaultProvider() {
        return Optional.ofNullable(defaultProvider).flatMap(this::getProvider);
    }

    public List<ModelInfo> getAllSupportedModels() {
        return providers.values().stream()
                .flatMap(p -> p.getSupportedModels().stream())
                .toList();
    }

    public Mono<ChatCompletionResponse> chat(ChatCompletionRequest request) {
        String model = request.getModel();
        String providerHint = request.getProvider();

        AiProvider provider;
        if (providerHint != null) {
            provider = providers.get(providerHint);
        } else {
            provider = getProviderForModel(model).orElseGet(() -> getDefaultProvider().orElse(null));
        }

        if (provider == null) {
            return Mono.error(new IllegalStateException("No AI provider available"));
        }

        return provider.chat(request);
    }

    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        AiProvider provider = getDefaultProvider().orElse(null);
        if (provider != null) {
            return provider.streamChat(request);
        }
        return Flux.empty();
    }

    public Optional<AiProvider> getProviderForModel(String model) {
        return providers.values().stream()
                .filter(p -> p.supportsModel(model))
                .findFirst();
    }

    public boolean supportsModel(String model) {
        return providers.values().stream().anyMatch(p -> p.supportsModel(model));
    }

    public List<String> getProviderNames() {
        return List.copyOf(providers.keySet());
    }
}