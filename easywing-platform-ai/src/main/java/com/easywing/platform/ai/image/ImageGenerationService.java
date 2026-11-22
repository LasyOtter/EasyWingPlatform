package com.easywing.platform.ai.image;

import com.easywing.platform.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 图像生成服务（PRD 4.6）。
 * <p>
 * 走 OpenAI 兼容的 {@code POST /images/generations}（文生图）与
 * {@code POST /images/editions}（图生图，multipart）。提供方由
 * {@code easywing.ai.image-generation.provider} 指定，可指向 {@code providers} 中的
 * OpenAI 兼容端点或 {@code customEndpoints} 中的任意兼容服务（Stable Diffusion / Flux
 * WebUI 等均提供 {@code /images/generations}）。默认关闭，密钥只经配置或环境变量注入。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final int MAX_PROMPT_CHARS = 16_000;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final AiProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ImageGenerationService(
            AiProperties properties, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        return config != null && config.isEnabled();
    }

    /** 文生图。 */
    public Mono<ImageGenerationResponse> generate(ImageGenerationRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt 不能为空");
        }
        if (request.getPrompt().length() > MAX_PROMPT_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt 超过 " + MAX_PROMPT_CHARS + " 字符上限");
        }
        int n = resolveN(request.getN());
        AiProperties.ImageGenerationConfig config = requireEnabled();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", StringUtils.hasText(request.getModel()) ? request.getModel() : config.getDefaultModel());
        body.put("prompt", request.getPrompt());
        body.put("n", n);
        if (StringUtils.hasText(request.getSize())) {
            body.put("size", request.getSize());
        } else if (StringUtils.hasText(config.getDefaultSize())) {
            body.put("size", config.getDefaultSize());
        }
        if (StringUtils.hasText(request.getQuality())) {
            body.put("quality", request.getQuality());
        }
        // 默认取 base64，避免把内网图片地址暴露给外部服务
        body.put(
                "response_format",
                StringUtils.hasText(request.getResponseFormat()) ? request.getResponseFormat() : "b64_json");

        Endpoint endpoint = resolveEndpoint();
        log.info("Image generation request: provider={}, model={}, n={}", endpoint.name(), body.get("model"), n);

        return webClientBuilder
                .baseUrl(endpoint.baseUrl().replaceAll("/+$", ""))
                .defaultHeader("Authorization", "Bearer " + endpoint.apiKey())
                .build()
                .post()
                .uri("/images/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .map(raw -> parse(raw, endpoint.name()))
                .onErrorMap(
                        ResponseStatusException.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图像生成请求失败: " + ex.getReason()));
    }

    /** 图生图（multipart）。 */
    public Mono<ImageGenerationResponse> edit(ImageEditRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt 不能为空");
        }
        if (request.getImage() == null || request.getImage().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "待编辑图像不能为空");
        }
        if (request.getImage().length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图像超过 " + MAX_IMAGE_BYTES + " 字节上限");
        }
        int n = resolveN(request.getN());
        AiProperties.ImageGenerationConfig config = requireEnabled();
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : config.getDefaultModel();
        String size = StringUtils.hasText(request.getSize()) ? request.getSize() : config.getDefaultSize();

        ByteArrayResource imageResource = new ByteArrayResource(request.getImage()) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(request.getFilename()) ? request.getFilename() : "image.png";
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", imageResource);
        form.add("prompt", request.getPrompt());
        form.add("model", model);
        form.add("n", n);
        if (StringUtils.hasText(size)) {
            form.add("size", size);
        }
        if (StringUtils.hasText(request.getQuality())) {
            form.add("quality", request.getQuality());
        }
        form.add(
                "response_format",
                StringUtils.hasText(request.getResponseFormat()) ? request.getResponseFormat() : "b64_json");

        Endpoint endpoint = resolveEndpoint();
        log.info(
                "Image edit request: provider={}, model={}, n={}, bytes={}",
                endpoint.name(),
                model,
                n,
                request.getImage().length);

        return webClientBuilder
                .baseUrl(endpoint.baseUrl().replaceAll("/+$", ""))
                .defaultHeader("Authorization", "Bearer " + endpoint.apiKey())
                .build()
                .post()
                .uri("/images/editions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .map(raw -> parse(raw, endpoint.name()))
                .onErrorMap(
                        ResponseStatusException.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图像编辑请求失败: " + ex.getReason()));
    }

    /** 当前可用的出图模型清单。 */
    public List<ImageModelInfo> models() {
        AiProperties.ImageGenerationConfig config = requireEnabled();
        List<ImageModelInfo> result = new ArrayList<>();
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            for (String model : config.getModels()) {
                result.add(ImageModelInfo.builder()
                        .id(model)
                        .provider(config.getProvider())
                        .build());
            }
            return result;
        }
        result.add(ImageModelInfo.builder()
                .id(config.getDefaultModel())
                .provider(config.getProvider())
                .defaultModel(true)
                .build());
        return result;
    }

    private AiProperties.ImageGenerationConfig requireEnabled() {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        if (config == null || !config.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "图像生成未启用（easywing.ai.image-generation.enabled）");
        }
        return config;
    }

    private int resolveN(Integer requested) {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        int max = config == null ? 1 : Math.max(1, config.getMaxN());
        int n = requested == null
                ? (config == null || config.getDefaultN() <= 0 ? 1 : config.getDefaultN())
                : requested;
        if (n < 1 || n > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "n 必须在 1.." + max + " 之间");
        }
        return n;
    }

    private Endpoint resolveEndpoint() {
        AiProperties.ImageGenerationConfig config = properties.getImageGeneration();
        String provider = StringUtils.hasText(config.getProvider()) ? config.getProvider() : "openai";

        AiProperties.ProviderConfig providerConfig = properties.getProviders().get(provider);
        if (providerConfig != null
                && StringUtils.hasText(providerConfig.getBaseUrl())
                && StringUtils.hasText(providerConfig.getApiKey())) {
            return new Endpoint(provider, providerConfig.getBaseUrl(), providerConfig.getApiKey());
        }

        AiProperties.CustomEndpointConfig custom =
                properties.getCustomEndpoints().get(provider);
        if (custom != null
                && custom.isEnabled()
                && StringUtils.hasText(custom.getBaseUrl())
                && StringUtils.hasText(custom.getApiKey())) {
            return new Endpoint(provider, custom.getBaseUrl(), custom.getApiKey());
        }

        if ("openai".equals(provider)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "图像生成缺少 OpenAI 凭据：请配置 easywing.ai.providers.openai.api-key 或改用 customEndpoints");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图像生成未找到可用提供方: " + provider);
    }

    /**
     * 解析响应体，独立为包内方法便于测试。
     */
    ImageGenerationResponse parse(String raw, String provider) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("error")) {
                String message = root.path("error").path("message").asText("未知错误");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图像服务返回错误: " + message);
            }
            List<ImageResult> results = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    results.add(ImageResult.builder()
                            .b64Json(text(node, "b64_json"))
                            .url(text(node, "url"))
                            .revisedPrompt(text(node, "revised_prompt"))
                            .build());
                }
            }
            if (results.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图像服务未返回任何图片");
            }
            return ImageGenerationResponse.builder()
                    .created(root.has("created") ? root.get("created").asLong() : null)
                    .model(root.has("model") ? root.get("model").asText() : null)
                    .provider(provider)
                    .data(results)
                    .build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析图像响应失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图像响应解析失败");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record Endpoint(String name, String baseUrl, String apiKey) {}

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageGenerationRequest {
        private String prompt;
        private String model;
        private Integer n;
        private String size;
        private String quality;
        /** b64_json 或 url */
        private String responseFormat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageEditRequest {
        private String prompt;
        private String model;
        private Integer n;
        private String size;
        private String quality;
        private String responseFormat;
        private byte[] image;
        private String filename;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageGenerationResponse {
        private Long created;
        private String model;
        private String provider;
        private List<ImageResult> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageResult {
        private String b64Json;
        private String url;
        private String revisedPrompt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageModelInfo {
        private String id;
        private String provider;
        private boolean defaultModel;
    }
}
