package com.easywing.platform.ai.config;

import com.easywing.platform.ai.provider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class AiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder aiWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper aiObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public AiProviderRegistry aiProviderRegistry(
            List<AiProvider> providers,
            AiProperties properties) {
        return new AiProviderRegistry(providers, properties);
    }

    @Bean
    public OpenAiProvider openAiProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiProperties properties) {
        var config = properties.getProviders().get("openai");
        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            log.info("OpenAI provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com/v1")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        AiProperties.OpenAiConfig openAiConfig = new AiProperties.OpenAiConfig();
        openAiConfig.setApiKey(config.getApiKey());
        openAiConfig.setBaseUrl(config.getBaseUrl());

        return new OpenAiProvider(webClient, openAiConfig);
    }

    @Bean
    public AnthropicProvider anthropicProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiProperties properties) {
        var config = properties.getProviders().get("anthropic");
        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            log.info("Anthropic provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.anthropic.com/v1")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        AiProperties.AnthropicConfig anthropicConfig = new AiProperties.AnthropicConfig();
        anthropicConfig.setApiKey(config.getApiKey());
        anthropicConfig.setBaseUrl(config.getBaseUrl());

        return new AnthropicProvider(webClient, anthropicConfig);
    }

    @Bean
    public GoogleAiProvider googleAiProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiProperties properties) {
        var config = properties.getProviders().get("google");
        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            log.info("Google AI provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : "https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        AiProperties.GoogleConfig googleConfig = new AiProperties.GoogleConfig();
        googleConfig.setApiKey(config.getApiKey());
        googleConfig.setBaseUrl(config.getBaseUrl());

        return new GoogleAiProvider(webClient, googleConfig);
    }
}