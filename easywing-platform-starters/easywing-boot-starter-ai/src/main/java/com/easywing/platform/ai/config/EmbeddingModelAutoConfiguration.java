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
package com.easywing.platform.ai.config;

import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "easywing.ai.embedding-model", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingModelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.embedding-model.openai", name = "api-key")
    @ConditionalOnMissingBean
    public EmbeddingModel openAiEmbeddingModel(AiProperties properties) {
        AiProperties.EmbeddingModel.OpenAiEmbedding config = properties.getEmbeddingModel().getOpenAi();
        log.info("Creating OpenAI EmbeddingModel with model: {}", config.getModelName());

        return OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .dimensions(config.getDimensions())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.embedding-model.qwen", name = "api-key")
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel qwenEmbeddingModel(AiProperties properties) {
        AiProperties.EmbeddingModel.QwenEmbedding config = properties.getEmbeddingModel().getQwen();
        log.info("Creating Qwen EmbeddingModel with model: {}", config.getModelName());

        return QwenEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.embedding-model.local", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel localEmbeddingModel(AiProperties properties) {
        log.info("Creating Local EmbeddingModel (AllMiniLmL6V2)");
        return new AllMiniLmL6V2EmbeddingModel();
    }
}