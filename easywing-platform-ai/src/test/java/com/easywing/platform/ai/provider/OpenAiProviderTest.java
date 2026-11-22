package com.easywing.platform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ModelInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OpenAiProviderTest {

    @Test
    void exposesEverySupportedModelWithPricing() {
        AiProperties.OpenAiConfig config = new AiProperties.OpenAiConfig();
        config.setApiKey("test-key");
        OpenAiProvider provider = new OpenAiProvider(WebClient.builder().build(), config);

        List<ModelInfo> models = provider.getSupportedModels();
        assertThat(models)
                .extracting(ModelInfo::getId)
                .containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo");

        for (ModelInfo model : models) {
            assertThat(provider.supportsModel(model.getId()))
                    .as("supportsModel should accept %s", model.getId())
                    .isTrue();
            assertThat(model.getInputCostPer1MTokens()).isPositive();
            assertThat(model.getOutputCostPer1MTokens()).isPositive();
        }
    }
}
