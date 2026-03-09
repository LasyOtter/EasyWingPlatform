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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "easywing.ai.chat-model", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatModelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatModelAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.chat-model.openai", name = "api-key")
    @ConditionalOnMissingBean
    public OpenAiChatModel openAiChatModel(AiProperties properties) {
        AiProperties.ChatModel.OpenAi config = properties.getChatModel().getOpenAi();
        log.info("Creating OpenAI ChatModel with model: {}", config.getModelName());

        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .topP(config.getTopP())
                .timeout(config.getTimeout())
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.chat-model.deepseek", name = "api-key")
    @ConditionalOnMissingBean(ChatModel.class)
    public OpenAiChatModel deepSeekChatModel(AiProperties properties) {
        AiProperties.ChatModel.DeepSeek config = properties.getChatModel().getDeepSeek();
        log.info("Creating DeepSeek ChatModel with model: {}", config.getModelName());

        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(config.getTimeout())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.chat-model.qwen", name = "api-key")
    @ConditionalOnMissingBean(ChatModel.class)
    public OpenAiChatModel qwenChatModel(AiProperties properties) {
        AiProperties.ChatModel.Qwen config = properties.getChatModel().getQwen();
        log.info("Creating Qwen ChatModel with model: {}", config.getModelName());

        return  OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.chat-model.ollama", name = "base-url")
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel ollamaChatModel(AiProperties properties) {
        AiProperties.ChatModel.Ollama config = properties.getChatModel().getOllama();
        log.info("Creating Ollama ChatModel with model: {}", config.getModelName());

        return OllamaChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .numPredict(config.getNumPredict())
                .build();
    }
}
