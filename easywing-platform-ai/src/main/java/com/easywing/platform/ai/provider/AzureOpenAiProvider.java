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

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ModelInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Azure OpenAI 提供方。
 *
 * <p>与 {@link OpenAiProvider} 的协议差异：
 *
 * <ul>
 *   <li>鉴权使用 {@code api-key} 请求头，而不是 {@code Authorization: Bearer}
 *   <li>请求路径为 {@code {base}/openai/deployments/{deployment}/chat/completions?api-version=...}
 *   <li>请求体的 {@code model} 字段是 deployment 名称，而不是公共 OpenAI 的模型 ID
 * </ul>
 *
 * <p>凭据只经配置或环境变量注入，默认关闭；未配置 {@code models} 时回退到 OpenAI 常用模型清单。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class AzureOpenAiProvider extends OpenAiProvider {

    private static final String PROVIDER_NAME = "azure-openai";
    private static final String DEFAULT_API_VERSION = "2024-06-01";

    private final AiProperties.AzureOpenAiConfig azureConfig;
    private final List<ModelInfo> supportedModels;

    public AzureOpenAiProvider(WebClient webClient, AiProperties.AzureOpenAiConfig config) {
        super(webClient, config);
        if (config == null || !StringUtils.hasText(config.getBaseUrl())) {
            throw new IllegalArgumentException("Azure OpenAI baseUrl is required");
        }
        this.azureConfig = config;
        this.supportedModels = resolveModels(config);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null
                && supportedModels.stream().anyMatch(info -> info.getId().equals(model));
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return supportedModels;
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return supportedModels.stream().map(ModelInfo::getId).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected String buildChatCompletionsUrl() {
        return stripTrailingSlash(getApiBaseUrl()) + "/openai/deployments/" + deploymentName()
                + "/chat/completions?api-version=" + apiVersion();
    }

    @Override
    protected String buildModelsUrl() {
        return stripTrailingSlash(getApiBaseUrl()) + "/openai/models?api-version=" + apiVersion();
    }

    @Override
    protected Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", getApiKey());
        return headers;
    }

    @Override
    protected String resolveDeploymentModel(String model) {
        return StringUtils.hasText(azureConfig.getDeployment()) ? azureConfig.getDeployment() : model;
    }

    private String deploymentName() {
        return StringUtils.hasText(azureConfig.getDeployment()) ? azureConfig.getDeployment() : "default";
    }

    private String apiVersion() {
        return StringUtils.hasText(azureConfig.getApiVersion()) ? azureConfig.getApiVersion() : DEFAULT_API_VERSION;
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private static List<ModelInfo> resolveModels(AiProperties.AzureOpenAiConfig config) {
        List<String> configured = config.getModels();
        if (configured == null
                || configured.isEmpty()
                || configured.stream().allMatch(model -> model == null || model.isBlank())) {
            return defaultModels();
        }
        return configured.stream()
                .filter(model -> model != null && !model.isBlank())
                .map(model -> ModelInfo.builder()
                        .id(model)
                        .provider(PROVIDER_NAME)
                        .name(model)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .build())
                .collect(Collectors.toUnmodifiableList());
    }

    private static List<ModelInfo> defaultModels() {
        return List.of(
                ModelInfo.builder()
                        .id(AbstractAiProvider.OpenAiModels.GPT_4O)
                        .provider(PROVIDER_NAME)
                        .name("GPT-4o")
                        .contextWindow(128000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(5.0)
                        .outputCostPer1MTokens(15.0)
                        .build(),
                ModelInfo.builder()
                        .id(AbstractAiProvider.OpenAiModels.GPT_4O_MINI)
                        .provider(PROVIDER_NAME)
                        .name("GPT-4o Mini")
                        .contextWindow(128000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(0.15)
                        .outputCostPer1MTokens(0.60)
                        .build(),
                ModelInfo.builder()
                        .id(AbstractAiProvider.OpenAiModels.GPT_4_TURBO)
                        .provider(PROVIDER_NAME)
                        .name("GPT-4 Turbo")
                        .contextWindow(128000)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(true)
                        .inputCostPer1MTokens(10.0)
                        .outputCostPer1MTokens(30.0)
                        .build(),
                ModelInfo.builder()
                        .id(AbstractAiProvider.OpenAiModels.GPT_35_TURBO)
                        .provider(PROVIDER_NAME)
                        .name("GPT-3.5 Turbo")
                        .contextWindow(16385)
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(false)
                        .inputCostPer1MTokens(0.5)
                        .outputCostPer1MTokens(1.5)
                        .build());
    }
}
