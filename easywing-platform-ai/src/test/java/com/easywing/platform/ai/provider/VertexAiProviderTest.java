package com.easywing.platform.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Vertex AI 提供方测试。
 * <p>
 * 验证提供方标识、模型清单回退与覆盖、Vertex 端点拼接和鉴权可用性判断。
 * 不发起真实网络请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class VertexAiProviderTest {

    private static final String BASE_URL =
            "https://us-central1-aiplatform.googleapis.com/v1/projects/demo/locations/us-central1/publishers/google/models";

    private VertexAiProvider provider(List<String> models) {
        AiProperties.VertexConfig config = new AiProperties.VertexConfig();
        config.setEnabled(true);
        config.setAccessToken("test-access-token");
        config.setBaseUrl(BASE_URL);
        config.setModels(models);
        return new VertexAiProvider(WebClient.builder().build(), config);
    }

    @Test
    void exposesProviderNameAndDefaultModels() {
        VertexAiProvider provider = provider(null);

        assertEquals("vertex", provider.getProviderName());
        assertEquals(3, provider.getSupportedModels().size());
        assertTrue(provider.supportsModel("gemini-2.0-flash-exp"));
        assertTrue(provider.supportsModel("gemini-1.5-pro"));
        assertFalse(provider.supportsModel("gpt-4o"));
        assertTrue(provider.getSupportedModels().get(0).isSupportsStreaming());
    }

    @Test
    void usesConfiguredModelsWhenProvided() {
        VertexAiProvider provider = provider(List.of("gemini-2.5-pro", "gemini-2.5-flash"));

        assertEquals(
                List.of("gemini-2.5-pro", "gemini-2.5-flash"),
                provider.getSupportedModels().stream().map(m -> m.getId()).toList());
        assertTrue(provider.supportsModel("gemini-2.5-pro"));
        assertFalse(provider.supportsModel("gemini-2.0-flash-exp"));
    }

    @Test
    void buildsGenerateContentUrls() {
        VertexAiProvider provider = provider(null);

        assertEquals(
                BASE_URL + "/gemini-2.0-flash-exp:generateContent", provider.buildUrl("gemini-2.0-flash-exp", false));
        assertEquals(
                BASE_URL + "/gemini-2.0-flash-exp:streamGenerateContent?alt=sse",
                provider.buildUrl("gemini-2.0-flash-exp", true));
    }

    @Test
    void trimsTrailingSlashFromBaseUrl() {
        AiProperties.VertexConfig config = new AiProperties.VertexConfig();
        config.setAccessToken("token");
        config.setBaseUrl(BASE_URL + "///");
        VertexAiProvider provider = new VertexAiProvider(WebClient.builder().build(), config);

        assertEquals(BASE_URL + "/gemini-1.5-flash:generateContent", provider.buildUrl("gemini-1.5-flash", false));
    }

    @Test
    void rejectsMissingBaseUrl() {
        AiProperties.VertexConfig config = new AiProperties.VertexConfig();
        config.setAccessToken("token");
        VertexAiProvider provider = new VertexAiProvider(WebClient.builder().build(), config);

        assertThrows(IllegalStateException.class, () -> provider.buildUrl("gemini-1.5-flash", false));
    }

    @Test
    void validateConnectionReflectsAccessToken() {
        StepVerifier.create(provider(null).validateConnection())
                .expectNext(true)
                .verifyComplete();

        AiProperties.VertexConfig config = new AiProperties.VertexConfig();
        config.setBaseUrl(BASE_URL);
        VertexAiProvider withoutToken = new VertexAiProvider(WebClient.builder().build(), config);

        StepVerifier.create(withoutToken.validateConnection()).expectNext(false).verifyComplete();
    }
}
