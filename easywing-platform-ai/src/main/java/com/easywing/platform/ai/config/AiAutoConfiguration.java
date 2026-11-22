package com.easywing.platform.ai.config;

import com.easywing.platform.ai.provider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class AiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder aiWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean(WebClient.class)
    public WebClient aiWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.defaultHeader("User-Agent", "EasyWing-AI/1.0").build();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper aiObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public AiProviderRegistry aiProviderRegistry(
            List<AiProvider> providers,
            AiProperties properties,
            WebClient.Builder webClientBuilder,
            com.easywing.platform.ai.observability.UsageLedger usageLedger) {
        return new AiProviderRegistry(providers, properties, webClientBuilder, usageLedger);
    }

    @Bean
    public OpenAiProvider openAiProvider(
            WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AiProperties properties) {
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
            WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AiProperties properties) {
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
            WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AiProperties properties) {
        var config = properties.getProviders().get("google");
        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            log.info("Google AI provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(
                        config.getBaseUrl() != null
                                ? config.getBaseUrl()
                                : "https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        AiProperties.GoogleConfig googleConfig = new AiProperties.GoogleConfig();
        googleConfig.setApiKey(config.getApiKey());
        googleConfig.setBaseUrl(config.getBaseUrl());

        return new GoogleAiProvider(webClient, googleConfig);
    }

    /**
     * Azure OpenAI 提供方。
     *
     * <p>仅在显式开启（{@code easywing.ai.azure.enabled=true}）且配置了 apiKey 与 baseUrl 时装配；
     * 凭据只经配置或环境变量注入，默认关闭。
     */
    @Bean
    public AzureOpenAiProvider azureOpenAiProvider(WebClient.Builder webClientBuilder, AiProperties properties) {
        AiProperties.AzureOpenAiConfig config = properties.getAzure();
        if (config == null
                || !config.isEnabled()
                || !StringUtils.hasText(config.getApiKey())
                || !StringUtils.hasText(config.getBaseUrl())) {
            log.info("Azure OpenAI provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(config.getBaseUrl().replaceAll("/+$", ""))
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        return new AzureOpenAiProvider(webClient, config);
    }

    /**
     * OpenAI Responses API 提供方。
     *
     * <p>仅在显式开启（{@code easywing.ai.responses.enabled=true}）且配置了 apiKey 时装配；
     * 凭据只经配置或环境变量注入，默认关闭。
     */
    @Bean
    public OpenAiResponsesProvider openAiResponsesProvider(
            WebClient.Builder webClientBuilder, AiProperties properties) {
        AiProperties.ResponsesApiConfig config = properties.getResponses();
        if (config == null || !config.isEnabled() || !StringUtils.hasText(config.getApiKey())) {
            log.info("OpenAI Responses API provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(
                        StringUtils.hasText(config.getBaseUrl())
                                ? config.getBaseUrl().replaceAll("/+$", "")
                                : "https://api.openai.com/v1")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        return new OpenAiResponsesProvider(webClient, config);
    }

    /**
     * Vertex AI（Gemini on Vertex）提供方。
     *
     * <p>仅在显式开启（{@code easywing.ai.vertex.enabled=true}）且配置了访问令牌与 baseUrl 时装配；
     * 令牌只经配置或环境变量注入，默认关闭。
     */
    @Bean
    public VertexAiProvider vertexAiProvider(WebClient.Builder webClientBuilder, AiProperties properties) {
        AiProperties.VertexConfig config = properties.getVertex();
        if (config == null
                || !config.isEnabled()
                || !StringUtils.hasText(config.getAccessToken())
                || !StringUtils.hasText(config.getBaseUrl())) {
            log.info("Vertex AI provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl(config.getBaseUrl().replaceAll("/+$", ""))
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        return new VertexAiProvider(webClient, config);
    }

    /**
     * AWS Bedrock 提供方。
     *
     * <p>仅在显式开启（{@code easywing.ai.bedrock.enabled=true}）且配置了 region、accessKey、
     * secretKey 时装配；凭据只经配置或环境变量注入，默认关闭。
     */
    @Bean(destroyMethod = "close")
    public BedrockAiProvider bedrockAiProvider(WebClient.Builder webClientBuilder, AiProperties properties) {
        AiProperties.BedrockConfig config = properties.getBedrock();
        if (config == null
                || !config.isEnabled()
                || !StringUtils.hasText(config.getRegion())
                || !StringUtils.hasText(config.getAccessKey())
                || !StringUtils.hasText(config.getSecretKey())) {
            log.info("Bedrock provider not configured, skipping");
            return null;
        }

        WebClient webClient = webClientBuilder
                .baseUrl("https://bedrock-runtime." + config.getRegion().trim() + ".amazonaws.com")
                .defaultHeader("User-Agent", "EasyWing-AI/1.0")
                .build();

        return new BedrockAiProvider(webClient, config);
    }
}
