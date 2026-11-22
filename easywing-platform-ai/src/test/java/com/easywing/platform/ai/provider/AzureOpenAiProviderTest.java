/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.config.AiProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Azure OpenAI 提供方单元测试
 *
 * <p>只验证协议适配（URL / 鉴权头 / 部署名映射 / 模型清单），不发起真实网络请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class AzureOpenAiProviderTest {

    private static AiProperties.AzureOpenAiConfig config(String baseUrl, String deployment, List<String> models) {
        AiProperties.AzureOpenAiConfig config = new AiProperties.AzureOpenAiConfig();
        config.setBaseUrl(baseUrl);
        config.setApiKey("test-key");
        config.setDeployment(deployment);
        config.setModels(models);
        return config;
    }

    private static AzureOpenAiProvider provider(AiProperties.AzureOpenAiConfig config) {
        return new AzureOpenAiProvider(WebClient.builder().build(), config);
    }

    @Test
    @DisplayName("使用 azure-openai 作为提供方名")
    void exposesProviderName() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", "dep-1", List.of("gpt-4o")));

        assertThat(provider.getProviderName()).isEqualTo("azure-openai");
    }

    @Test
    @DisplayName("Chat Completions 路径使用 deployment 与 api-version")
    void buildsDeploymentChatUrl() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com/", "my-gpt4o", List.of("gpt-4o")));

        assertThat(provider.buildChatCompletionsUrl())
                .isEqualTo(
                        "https://res.openai.azure.com/openai/deployments/my-gpt4o/chat/completions?api-version=2024-06-01");
    }

    @Test
    @DisplayName("模型列表路径带 api-version")
    void buildsModelsUrl() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", "dep", List.of("gpt-4o")));

        assertThat(provider.buildModelsUrl())
                .isEqualTo("https://res.openai.azure.com/openai/models?api-version=2024-06-01");
    }

    @Test
    @DisplayName("鉴权使用 api-key 头而非 Bearer")
    void usesApiKeyHeader() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", "dep", List.of("gpt-4o")));

        Map<String, String> headers = provider.buildAuthHeaders();

        assertThat(headers).containsEntry("api-key", "test-key");
        assertThat(headers).doesNotContainKey("Authorization");
    }

    @Test
    @DisplayName("请求体的 model 字段映射为 deployment 名")
    void mapsModelToDeployment() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", "my-gpt4o", List.of("gpt-4o")));

        assertThat(provider.resolveDeploymentModel("gpt-4o")).isEqualTo("my-gpt4o");
    }

    @Test
    @DisplayName("未配置 deployment 时回退到逻辑模型名")
    void fallsBackToModelWhenNoDeployment() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", null, List.of("gpt-4o")));

        assertThat(provider.resolveDeploymentModel("gpt-4o")).isEqualTo("gpt-4o");
        assertThat(provider.buildChatCompletionsUrl()).contains("/openai/deployments/default/chat/completions");
    }

    @Test
    @DisplayName("只声明已配置的模型，且 provider 标注为 azure-openai")
    void declaresConfiguredModelsOnly() {
        AzureOpenAiProvider provider =
                provider(config("https://res.openai.azure.com", "dep", List.of("gpt-4o", "gpt-4o-mini")));

        assertThat(provider.supportsModel("gpt-4o")).isTrue();
        assertThat(provider.supportsModel("gpt-4o-mini")).isTrue();
        assertThat(provider.supportsModel("claude-3-5-sonnet-20241022")).isFalse();
        assertThat(provider.getSupportedModels())
                .allSatisfy(info -> assertThat(info.getProvider()).isEqualTo("azure-openai"));
    }

    @Test
    @DisplayName("未配置 models 时回退到常用 OpenAI 模型清单")
    void fallsBackToDefaultModels() {
        AzureOpenAiProvider provider = provider(config("https://res.openai.azure.com", "dep", List.of()));

        assertThat(provider.getSupportedModels()).isNotEmpty();
        assertThat(provider.supportsModel("gpt-4o")).isTrue();
    }

    @Test
    @DisplayName("缺少 baseUrl 时拒绝构造")
    void rejectsMissingBaseUrl() {
        assertThatThrownBy(() -> provider(config("  ", "dep", List.of("gpt-4o"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
    }
}
