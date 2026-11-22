package com.easywing.platform.ai.multimodal;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 多模态理解服务（PRD 4.6）。
 * <p>
 * 当前真实支持 TEXT 与 IMAGE：图像经 {@code image_url} 内容块（URL 或 base64 Data URI）
 * 交给具备视觉能力的模型，回答由模型返回，不做任何模拟。AUDIO / VIDEO / DOCUMENT
 * 尚未接入真实后端，显式抛出 {@link IllegalArgumentException} 而不是返回假数据。
 */
@Slf4j
@Service
public class MultiModalService {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 100_000;
    private static final Set<ModalityType> SUPPORTED_TYPES = EnumSet.of(ModalityType.TEXT, ModalityType.IMAGE);

    private final AiProviderRegistry providerRegistry;
    private final AiProperties properties;

    public MultiModalService(AiProviderRegistry providerRegistry, AiProperties properties) {
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    public Mono<MultiModalResponse> processMultiModal(MultiModalInput input) {
        if (input == null
                || input.getComponents() == null
                || input.getComponents().isEmpty()) {
            return Mono.error(new IllegalArgumentException("多模态输入至少需要一个内容组件"));
        }

        long startTime = System.currentTimeMillis();
        List<ChatMessage.ContentPart> parts = new ArrayList<>();
        List<ComponentResult> componentResults = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        for (MultiModalComponent component : input.getComponents()) {
            ModalityType type = component.getType();
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                return Mono.error(new IllegalArgumentException("不支持的多模态类型: " + type + "（当前支持 TEXT、IMAGE）"));
            }
            if (type == ModalityType.TEXT) {
                String text = component.getTextContent() == null ? "" : component.getTextContent();
                if (text.length() > MAX_TEXT_CHARS) {
                    return Mono.error(new IllegalArgumentException("TEXT 组件超过 " + MAX_TEXT_CHARS + " 字符上限"));
                }
                parts.add(textPart(text));
                textContent.append(text);
                componentResults.add(ComponentResult.builder()
                        .type(type)
                        .content(text)
                        .metadata(Map.of("length", text.length()))
                        .build());
            } else {
                ImageRef image = resolveImage(component);
                parts.add(ChatMessage.ContentPart.builder()
                        .type("image_url")
                        .imageUrl(ChatMessage.ImageUrl.builder()
                                .url(image.dataUri())
                                .detail(input.getDetail())
                                .build())
                        .build());
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("byteSize", image.byteSize());
                if (image.mediaType() != null) {
                    metadata.put("mediaType", image.mediaType());
                }
                metadata.put("source", image.source());
                componentResults.add(ComponentResult.builder()
                        .type(type)
                        .content(null)
                        .metadata(metadata)
                        .build());
            }
        }

        String query = input.getQuery() == null ? "" : input.getQuery().trim();
        if (!query.isBlank()) {
            parts.add(0, textPart(query));
            textContent.insert(0, query);
        }
        if (textContent.toString().isBlank()) {
            return Mono.error(new IllegalArgumentException("缺少文本指令：请提供 query 或 TEXT 组件"));
        }

        String model = resolveModel(input.getModel());
        ChatMessage message = ChatMessage.builder()
                .role(ChatMessage.ROLE_USER)
                .content(textContent.toString())
                .contentParts(parts)
                .build();

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .userId(input.getUserId())
                .tenantId(input.getTenantId())
                .messages(List.of(message))
                .temperature(0.2)
                .build();

        log.info(
                "Multi-modal request: model={}, components={}",
                model,
                input.getComponents().size());

        return providerRegistry.chat(request).flatMap(response -> {
            String answer = extractContent(response);
            if (answer.isBlank()) {
                return Mono.error(new IllegalStateException("模型未返回内容"));
            }
            return Mono.just(MultiModalResponse.builder()
                    .answer(answer)
                    .componentResults(componentResults)
                    .model(model)
                    .totalProcessingTime(System.currentTimeMillis() - startTime)
                    .build());
        });
    }

    /**
     * 解析视觉模型：优先请求指定的模型，其次配置项，最后取第一个声明 {@code supportsVision} 的模型。
     */
    private String resolveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            if (!providerRegistry.supportsModel(requested)) {
                throw new IllegalArgumentException("模型不可用: " + requested);
            }
            return requested;
        }
        AiProperties.MultimodalConfig config = properties.getMultimodal();
        if (config != null
                && config.getDefaultModel() != null
                && !config.getDefaultModel().isBlank()) {
            String configured = config.getDefaultModel();
            if (!providerRegistry.supportsModel(configured)) {
                throw new IllegalArgumentException("配置的默认视觉模型不可用: " + configured);
            }
            return configured;
        }
        return providerRegistry.getAllSupportedModels().stream()
                .filter(ModelInfo::isSupportsVision)
                .map(ModelInfo::getId)
                .filter(providerRegistry::supportsModel)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("未找到支持图像的模型：请在 easywing.ai.multimodal.default-model 指定视觉模型"));
    }

    private ImageRef resolveImage(MultiModalComponent component) {
        if (component.getImageUrl() != null && !component.getImageUrl().isBlank()) {
            return ImageRef.url(component.getImageUrl().trim());
        }
        byte[] data = component.getImageData();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("IMAGE 组件缺少图像数据");
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图像超过 " + MAX_IMAGE_BYTES + " 字节上限");
        }
        String mediaType = probeMediaType(data);
        String dataUri = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(data);
        return ImageRef.base64(data.length, mediaType, dataUri);
    }

    private static String probeMediaType(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return "image/webp";
        }
        return "image/png";
    }

    private static ChatMessage.ContentPart textPart(String text) {
        return ChatMessage.ContentPart.builder()
                .type("text")
                .text(text == null ? "" : text)
                .build();
    }

    private static String extractContent(ChatCompletionResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()) {
            return "";
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if (choice == null) {
            return "";
        }
        if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
            return choice.getMessage().getContent();
        }
        return choice.getDelta() != null && choice.getDelta().getContent() != null
                ? choice.getDelta().getContent()
                : "";
    }

    private record ImageRef(int byteSize, String mediaType, String source, String dataUri) {
        static ImageRef url(String url) {
            return new ImageRef(0, null, "url", url);
        }

        static ImageRef base64(int byteSize, String mediaType, String dataUri) {
            return new ImageRef(byteSize, mediaType, "base64", dataUri);
        }
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiModalInput {
        private String query;
        private String model;
        private String detail;
        private String userId;
        private String tenantId;
        private List<MultiModalComponent> components;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiModalComponent {
        private ModalityType type;
        private String textContent;
        private byte[] imageData;
        private String imageUrl;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiModalResponse {
        private String answer;
        private String model;
        private List<ComponentResult> componentResults;
        private long totalProcessingTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentResult {
        private ModalityType type;
        private String content;
        private Map<String, Object> metadata;
    }

    public enum ModalityType {
        TEXT,
        IMAGE,
        AUDIO,
        VIDEO,
        DOCUMENT
    }
}
