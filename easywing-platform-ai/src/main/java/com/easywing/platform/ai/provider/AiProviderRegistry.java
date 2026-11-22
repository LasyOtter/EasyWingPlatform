package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.observability.UsageLedger;
import com.easywing.platform.ai.observability.UsageRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class AiProviderRegistry {

    private static final double TOKENS_PER_MILLION = 1_000_000.0;

    private final Map<String, AiProvider> providers = new ConcurrentHashMap<>();
    private final AiProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final UsageLedger usageLedger;
    private String defaultProvider;

    public AiProviderRegistry(
            List<AiProvider> providers,
            AiProperties properties,
            WebClient.Builder webClientBuilder,
            UsageLedger usageLedger) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.usageLedger = usageLedger;

        for (AiProvider provider : providers) {
            if (provider != null) {
                registerProvider(provider);
            }
        }

        properties.getCustomEndpoints().forEach((id, endpoint) -> {
            if (endpoint != null && endpoint.isEnabled()) {
                registerProvider(new CustomOpenAiProvider(id, endpoint, webClientBuilder));
            }
        });

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

    public synchronized AiProvider registerCustomEndpoint(String id, AiProperties.CustomEndpointConfig endpoint) {
        CustomOpenAiProvider provider = new CustomOpenAiProvider(id, endpoint, webClientBuilder);
        providers.put(provider.getProviderName(), provider);
        properties.getCustomEndpoints().put(id, endpoint);
        return provider;
    }

    public void removeCustomEndpoint(String id) {
        providers.remove("custom-" + id);
        properties.getCustomEndpoints().remove(id);
    }

    public Map<String, AiProperties.CustomEndpointConfig> getCustomEndpoints() {
        Map<String, AiProperties.CustomEndpointConfig> result = new TreeMap<>();
        properties.getCustomEndpoints().forEach((id, endpoint) -> {
            AiProperties.CustomEndpointConfig safe = new AiProperties.CustomEndpointConfig();
            safe.setEnabled(endpoint.isEnabled());
            safe.setName(endpoint.getName());
            safe.setBaseUrl(endpoint.getBaseUrl());
            safe.setModels(endpoint.getModels());
            result.put(id, safe);
        });
        return Map.copyOf(result);
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

        AiProvider provider = selectProvider(request);

        if (provider == null) {
            return Mono.error(new IllegalStateException("No AI provider available"));
        }
        if (providerHint != null && !provider.supportsModel(model)) {
            return Mono.error(new IllegalArgumentException("The selected provider does not support this model"));
        }

        return provider.chat(request)
                .doOnNext(response -> recordUsage(request, provider.getProviderName(), usageOf(response)));
    }

    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        AiProvider provider = selectProvider(request);
        if (provider == null) {
            return Flux.error(new IllegalStateException("No AI provider available for the requested model"));
        }
        AtomicReference<ChatCompletionResponse.Usage> lastUsage = new AtomicReference<>();
        return provider.streamChat(request)
                .doOnNext(chunk -> {
                    if (chunk != null && chunk.getUsage() != null) {
                        lastUsage.set(chunk.getUsage());
                    }
                })
                .doOnComplete(() -> recordUsage(request, provider.getProviderName(), lastUsage.get()));
    }

    private ChatCompletionResponse.Usage usageOf(ChatCompletionResponse response) {
        return response == null ? null : response.getUsage();
    }

    /**
     * 记录一次模型调用的 Token 用量与估算成本。
     */
    private void recordUsage(ChatCompletionRequest request, String providerName, ChatCompletionResponse.Usage usage) {
        if (request == null || usage == null) {
            return;
        }
        double cost = 0.0;
        Optional<ModelInfo> modelInfo = findModelInfo(request.getModel());
        if (modelInfo.isPresent()) {
            ModelInfo info = modelInfo.get();
            cost = usage.getPromptTokens() * info.getInputCostPer1MTokens() / TOKENS_PER_MILLION
                    + usage.getCompletionTokens() * info.getOutputCostPer1MTokens() / TOKENS_PER_MILLION;
        }
        usageLedger.record(UsageRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .provider(providerName)
                .model(request.getModel())
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .estimatedCostUsd(cost)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 在所有已注册 Provider 中查找模型元数据，用于成本估算。
     */
    public Optional<ModelInfo> findModelInfo(String model) {
        if (model == null) {
            return Optional.empty();
        }
        return providers.values().stream()
                .map(provider -> provider.getModelInfo(model))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private AiProvider selectProvider(ChatCompletionRequest request) {
        if (request.getProvider() != null) {
            AiProvider provider = providers.get(request.getProvider());
            return provider != null && provider.supportsModel(request.getModel()) ? provider : null;
        }
        return getProviderForModel(request.getModel())
                .orElseGet(() -> getDefaultProvider().orElse(null));
    }

    public Optional<AiProvider> getProviderForModel(String model) {
        return providers.values().stream().filter(p -> p.supportsModel(model)).findFirst();
    }

    public boolean supportsModel(String model) {
        return providers.values().stream().anyMatch(p -> p.supportsModel(model));
    }

    public List<String> getProviderNames() {
        return providers.keySet().stream().sorted().toList();
    }
}
