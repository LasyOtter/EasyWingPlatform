package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ModelInfo;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI-compatible provider backed by an administrator-defined endpoint.
 */
public class CustomOpenAiProvider extends OpenAiProvider {

    private final String id;
    private final String displayName;
    private final List<String> models;

    public CustomOpenAiProvider(
            String id, AiProperties.CustomEndpointConfig endpoint, WebClient.Builder webClientBuilder) {
        super(customWebClient(endpoint, webClientBuilder), customConfig(endpoint));
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Custom endpoint id is invalid");
        }
        if (endpoint.getBaseUrl() == null || endpoint.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Custom endpoint baseUrl is required");
        }
        if (endpoint.getModels() == null
                || endpoint.getModels().isEmpty()
                || endpoint.getModels().stream().anyMatch(model -> model == null || model.isBlank())) {
            throw new IllegalArgumentException("At least one valid model is required");
        }
        this.id = "custom-" + id;
        this.displayName = endpoint.getName() == null || endpoint.getName().isBlank() ? id : endpoint.getName();
        this.models = List.copyOf(endpoint.getModels());
    }

    private static WebClient customWebClient(
            AiProperties.CustomEndpointConfig endpoint, WebClient.Builder webClientBuilder) {
        validateEndpoint(endpoint);
        return webClientBuilder
                .clone()
                .baseUrl(endpoint.getBaseUrl().replaceAll("/+$", ""))
                .build();
    }

    private static AiProperties.OpenAiConfig customConfig(AiProperties.CustomEndpointConfig endpoint) {
        validateEndpoint(endpoint);
        AiProperties.OpenAiConfig config = new AiProperties.OpenAiConfig();
        config.setApiKey(endpoint.getApiKey() == null ? "" : endpoint.getApiKey());
        config.setBaseUrl(endpoint.getBaseUrl().replaceAll("/+$", ""));
        return config;
    }

    private static void validateEndpoint(AiProperties.CustomEndpointConfig endpoint) {
        if (endpoint == null
                || endpoint.getBaseUrl() == null
                || endpoint.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Custom endpoint baseUrl is required");
        }
        if (endpoint.getModels() == null
                || endpoint.getModels().isEmpty()
                || endpoint.getModels().stream().anyMatch(model -> model == null || model.isBlank())) {
            throw new IllegalArgumentException("At least one valid model is required");
        }
    }

    @Override
    public String getProviderName() {
        return id;
    }

    @Override
    public boolean supportsModel(String model) {
        return models.contains(model);
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.copyOf(models);
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return models.stream()
                .map(model -> ModelInfo.builder()
                        .id(model)
                        .provider(id)
                        .name(displayName + " / " + model)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .build())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public ModelInfo getModelInfo(String model) {
        return getSupportedModels().stream()
                .filter(info -> info.getId().equals(model))
                .findFirst()
                .orElse(null);
    }
}
