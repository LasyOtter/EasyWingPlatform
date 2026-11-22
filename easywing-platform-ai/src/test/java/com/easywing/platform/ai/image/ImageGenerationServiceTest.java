package com.easywing.platform.ai.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.image.ImageGenerationService.ImageGenerationRequest;
import com.easywing.platform.ai.image.ImageGenerationService.ImageGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * 图像生成服务测试（PRD 4.6）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ImageGenerationServiceTest {

    private AiProperties properties;
    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        service = new ImageGenerationService(properties, WebClient.builder(), new ObjectMapper());
    }

    @Test
    void parsesB64JsonUrlAndRevisedPrompt() {
        String raw =
                """
                {
                  "created": 1700000000,
                  "model": "gpt-image-1",
                  "data": [
                    {"b64_json": "AAAA", "revised_prompt": "a cat"},
                    {"url": "https://example.com/x.png"}
                  ]
                }
                """;

        ImageGenerationResponse response = service.parse(raw, "openai");

        assertEquals(1700000000L, response.getCreated());
        assertEquals("gpt-image-1", response.getModel());
        assertEquals("openai", response.getProvider());
        assertEquals(2, response.getData().size());
        assertEquals("AAAA", response.getData().get(0).getB64Json());
        assertEquals("a cat", response.getData().get(0).getRevisedPrompt());
        assertEquals("https://example.com/x.png", response.getData().get(1).getUrl());
    }

    @Test
    void mapsUpstreamErrorToBadGateway() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.parse("{\"error\":{\"message\":\"content policy\"}}", "openai"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("content policy"));
    }

    @Test
    void rejectsResponseWithoutImages() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.parse("{\"data\":[]}", "openai"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void rejectsMalformedResponse() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.parse("not-json", "openai"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void generateFailsWhenDisabled() {
        properties.getImageGeneration().setEnabled(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(
                        ImageGenerationRequest.builder().prompt("a cat").build()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertFalse(service.isEnabled());
    }

    @Test
    void generateFailsWithoutPrompt() {
        properties.getImageGeneration().setEnabled(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(ImageGenerationRequest.builder().build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateRejectsOversizedPrompt() {
        properties.getImageGeneration().setEnabled(true);
        String prompt = "x".repeat(20_000);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(
                        ImageGenerationRequest.builder().prompt(prompt).build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateRejectsNOutOfRange() {
        properties.getImageGeneration().setEnabled(true);
        properties.getImageGeneration().setMaxN(4);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(
                        ImageGenerationRequest.builder().prompt("a cat").n(9).build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateReportsMissingCredentials() {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        config.setEnabled(true);
        config.setProvider("openai");
        properties.setProviders(new HashMap<>());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(
                        ImageGenerationRequest.builder().prompt("a cat").build()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("凭据"));
    }

    @Test
    void modelsReturnsConfiguredListWhenEnabled() {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        config.setEnabled(true);
        config.setProvider("openai");
        config.setModels(List.of("gpt-image-1", "dall-e-3"));

        List<?> models = service.models();

        assertEquals(2, models.size());
        assertNotNull(models.get(0));
    }

    @Test
    void modelsFailsWhenDisabled() {
        properties.getImageGeneration().setEnabled(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.models());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }
}
