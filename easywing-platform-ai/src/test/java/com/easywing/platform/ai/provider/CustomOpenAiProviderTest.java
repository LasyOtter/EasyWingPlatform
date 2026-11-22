package com.easywing.platform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class CustomOpenAiProviderTest {

    @Test
    void registersConfiguredModelsWithoutExposingEndpointCredentials() {
        AiProperties.CustomEndpointConfig endpoint = new AiProperties.CustomEndpointConfig();
        endpoint.setName("Local AI");
        endpoint.setBaseUrl("http://localhost:11434/v1/");
        endpoint.setApiKey("secret-key");
        endpoint.setModels(List.of("qwen2.5", "llama3"));

        CustomOpenAiProvider provider = new CustomOpenAiProvider("local", endpoint, WebClient.builder());

        assertThat(provider.getProviderName()).isEqualTo("custom-local");
        assertThat(provider.supportsModel("qwen2.5")).isTrue();
        assertThat(provider.supportsModel("unknown")).isFalse();
        assertThat(provider.getSupportedModels()).extracting("provider").containsOnly("custom-local");
        assertThat(provider.getSupportedModels()).extracting("id").containsExactly("qwen2.5", "llama3");
    }

    @Test
    void rejectsEndpointsWithoutModelsOrWithInvalidId() {
        AiProperties.CustomEndpointConfig endpoint = new AiProperties.CustomEndpointConfig();
        endpoint.setBaseUrl("http://localhost:11434/v1");
        endpoint.setModels(List.of());

        assertThatThrownBy(() -> new CustomOpenAiProvider("local", endpoint, WebClient.builder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }
}
