package com.easywing.platform.ai.multimodal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.multimodal.MultiModalService.ModalityType;
import com.easywing.platform.ai.multimodal.MultiModalService.MultiModalComponent;
import com.easywing.platform.ai.multimodal.MultiModalService.MultiModalInput;
import com.easywing.platform.ai.multimodal.MultiModalService.MultiModalResponse;
import com.easywing.platform.ai.observability.UsageLedger;
import com.easywing.platform.ai.provider.AiProvider;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多模态理解服务测试（PRD 4.6）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class MultiModalServiceTest {

    private static final String VISION_MODEL = "vision-model";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private AiProperties properties;
    private StubVisionProvider provider;
    private AtomicReference<ChatCompletionRequest> captured;
    private MultiModalService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setProviders(new HashMap<>());
        properties.setCustomEndpoints(new HashMap<>());

        provider = new StubVisionProvider();
        captured = new AtomicReference<>();
        provider.captured = captured;

        AiProviderRegistry registry =
                new AiProviderRegistry(List.of(provider), properties, WebClient.builder(), (UsageLedger) null);
        service = new MultiModalService(registry, properties);
    }

    @Test
    void rejectsUnsupportedModalities() {
        assertThrows(IllegalArgumentException.class, () -> service.processMultiModal(input(MultiModalComponent.builder()
                        .type(ModalityType.AUDIO)
                        .textContent("x")
                        .build()))
                .block());
        assertThrows(IllegalArgumentException.class, () -> service.processMultiModal(input(MultiModalComponent.builder()
                        .type(ModalityType.DOCUMENT)
                        .textContent("x")
                        .build()))
                .block());
    }

    @Test
    void requiresTextInstruction() {
        MultiModalInput input = MultiModalInput.builder()
                .components(List.of(MultiModalComponent.builder()
                        .type(ModalityType.IMAGE)
                        .imageData(PNG)
                        .build()))
                .build();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.processMultiModal(input)
                        .block());

        assertTrue(ex.getMessage().contains("缺少文本指令"));
    }

    @Test
    void resolvesVisionModelAndBuildsImageUrlParts() {
        MultiModalResponse response = service.processMultiModal(input(MultiModalComponent.builder()
                        .type(ModalityType.IMAGE)
                        .imageData(PNG)
                        .build()))
                .block();

        assertNotNull(response);
        assertEquals("这是一只猫", response.getAnswer());
        assertEquals(VISION_MODEL, response.getModel());
        assertFalse(response.getComponentResults().isEmpty(), "componentResults 不应被丢弃");

        ChatCompletionRequest request = captured.get();
        ChatMessage message = request.getMessages().get(0);
        assertEquals(2, message.getContentParts().size(), "query + image 两个内容块");
        assertEquals("text", message.getContentParts().get(0).getType());
        assertEquals("image_url", message.getContentParts().get(1).getType());

        String url = message.getContentParts().get(1).getImageUrl().getUrl();
        assertTrue(url.startsWith("data:image/png;base64,"), "应按探测出的 PNG 类型构造 Data URI");

        var imageMeta = response.getComponentResults().get(0).getMetadata();
        assertEquals("base64", imageMeta.get("source"));
        assertEquals(PNG.length, imageMeta.get("byteSize"));
    }

    @Test
    void usesConfiguredDefaultModel() {
        properties.getMultimodal().setDefaultModel(VISION_MODEL);

        MultiModalResponse response = service.processMultiModal(input(MultiModalComponent.builder()
                        .type(ModalityType.IMAGE)
                        .imageData(PNG)
                        .build()))
                .block();

        assertEquals(VISION_MODEL, response.getModel());
    }

    @Test
    void rejectsUnavailableRequestedModel() {
        MultiModalInput input = input(MultiModalComponent.builder()
                .type(ModalityType.TEXT)
                .textContent("hi")
                .build());
        input.setModel("no-such-model");

        assertThrows(IllegalArgumentException.class, () -> service.processMultiModal(input)
                .block());
    }

    @Test
    void rejectsOversizedImage() {
        byte[] huge = new byte[10 * 1024 * 1024 + 1];

        MultiModalInput input = input(MultiModalComponent.builder()
                .type(ModalityType.IMAGE)
                .imageData(huge)
                .build());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.processMultiModal(input)
                        .block());
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    void failsWhenModelReturnsNoContent() {
        provider.answer = "";

        MultiModalInput input = input(MultiModalComponent.builder()
                .type(ModalityType.TEXT)
                .textContent("hi")
                .build());

        assertThrows(IllegalStateException.class, () -> service.processMultiModal(input)
                .block());
    }

    private static MultiModalInput input(MultiModalComponent component) {
        return MultiModalInput.builder()
                .query("这张图里有什么？")
                .components(List.of(component))
                .build();
    }

    private static final class StubVisionProvider implements AiProvider {

        private AtomicReference<ChatCompletionRequest> captured;
        private String answer = "这是一只猫";

        @Override
        public String getProviderName() {
            return "stub";
        }

        @Override
        public boolean supportsModel(String model) {
            return VISION_MODEL.equals(model);
        }

        @Override
        public List<ModelInfo> getSupportedModels() {
            return List.of(ModelInfo.builder()
                    .id(VISION_MODEL)
                    .provider("stub")
                    .supportsVision(true)
                    .build());
        }

        @Override
        public ModelInfo getModelInfo(String model) {
            return ModelInfo.builder().id(model).provider("stub").build();
        }

        @Override
        public Mono<ChatCompletionResponse> chat(ChatCompletionRequest request) {
            captured.set(request);
            if (answer.isBlank()) {
                return Mono.just(ChatCompletionResponse.builder()
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .message(ChatMessage.builder()
                                        .role("assistant")
                                        .content("")
                                        .build())
                                .build()))
                        .build());
            }
            return Mono.just(ChatCompletionResponse.builder()
                    .choices(List.of(ChatCompletionResponse.Choice.builder()
                            .message(ChatMessage.builder()
                                    .role("assistant")
                                    .content(answer)
                                    .build())
                            .build()))
                    .build());
        }

        @Override
        public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
            return Flux.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Boolean> validateConnection() {
            return Mono.just(true);
        }
    }
}
