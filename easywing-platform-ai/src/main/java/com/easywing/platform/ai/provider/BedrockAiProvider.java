package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ModelInfo;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * AWS Bedrock 提供方（Converse / ConverseStream 协议）。
 * <p>
 * 使用 AWS SDK v2 的 {@link BedrockRuntimeAsyncClient}：该版本 SDK 的同步客户端已移除流式接口，
 * 且 SigV4 签名与二进制 event-stream 解码由 SDK 负责，避免手工实现带来的安全与兼容风险。
 * <p>
 * 凭据（region / accessKey / secretKey / sessionToken）只经配置或环境变量注入，禁止硬编码；
 * 未显式开启时不会装配该提供方。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class BedrockAiProvider extends AbstractAiProvider {

    private static final String PROVIDER_NAME = "bedrock";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Bedrock 模型标识含厂商前缀与版本后缀（形如 {@code anthropic.claude-3-5-sonnet-20241022-v2:0}）。
     * 仅作为未配置 {@code models} 时的回退清单，实际可用模型由租户的 Bedrock 授权范围决定。
     */
    private static final List<String> DEFAULT_MODELS = List.of(
            "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "anthropic.claude-3-haiku-20240307-v1:0",
            "amazon.titan-text-express-v1",
            "meta.llama3-8b-instruct-v1:0",
            "mistral.mistral-7b-instruct-v0:2",
            "cohere.command-r-v1:0");

    private final List<String> configuredModels;
    private final BedrockRuntimeAsyncClient client;

    public BedrockAiProvider(WebClient webClient, AiProperties.BedrockConfig config) {
        this(webClient, config, null);
    }

    /**
     * 允许注入客户端（测试用）：{@code client} 为 {@code null} 时按配置构建真实客户端。
     */
    BedrockAiProvider(WebClient webClient, AiProperties.BedrockConfig config, BedrockRuntimeAsyncClient client) {
        super(
                webClient,
                Map.of(
                        "region", nullToEmpty(config.getRegion()),
                        "accessKey", nullToEmpty(config.getAccessKey()),
                        "secretKey", nullToEmpty(config.getSecretKey()),
                        "endpoint", nullToEmpty(config.getEndpoint())));
        this.configuredModels = config.getModels() == null
                ? List.of()
                : config.getModels().stream()
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
        this.client = client != null ? client : buildClient(config);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsModel(String model) {
        return effectiveModels().contains(model);
    }

    @Override
    public List<ModelInfo> getSupportedModels() {
        return effectiveModels().stream()
                .map(model -> ModelInfo.builder()
                        .id(model)
                        .provider(PROVIDER_NAME)
                        .name(model)
                        .contextWindow(contextWindow(model))
                        .supportsStreaming(true)
                        .supportsTools(true)
                        .supportsVision(model.toLowerCase(Locale.ROOT).contains("claude"))
                        .inputCostPer1MTokens(0.0)
                        .outputCostPer1MTokens(0.0)
                        .build())
                .toList();
    }

    @Override
    protected Set<String> getSupportedModelIds() {
        return Set.copyOf(effectiveModels());
    }

    private List<String> effectiveModels() {
        return configuredModels.isEmpty() ? DEFAULT_MODELS : configuredModels;
    }

    /**
     * Claude 3/4 上下文窗口为 200k，其余列出的模型按 128k 计。
     * 该值仅用于展示与预算估算，实际以模型清单为准。
     */
    private static int contextWindow(String model) {
        return model.toLowerCase(Locale.ROOT).contains("claude") ? 200000 : 128000;
    }

    @Override
    protected String getApiBaseUrl() {
        String region = config.get("region");
        return StringUtils.hasText(region) ? "https://bedrock-runtime." + region + ".amazonaws.com" : "";
    }

    @Override
    protected String getApiKey() {
        return config.get("accessKey");
    }

    /**
     * 非流式调用。{@code Mono.fromFuture} 的 Supplier 重载保证请求在订阅时才发出。
     */
    @Override
    public Mono<ChatCompletionResponse> chat(ChatCompletionRequest request) {
        ConverseRequest awsRequest = BedrockProtocol.buildRequest(request.getModel(), request);
        return Mono.fromFuture(() -> client.converse(awsRequest))
                .map(response -> BedrockProtocol.parse(response, request.getModel()))
                .timeout(DEFAULT_TIMEOUT)
                .doOnError(e -> log.error("Bedrock converse error: {}", e.getMessage()));
    }

    /**
     * 流式调用：SDK 通过 {@link ConverseStreamResponseHandler} 的 Visitor 回调推送事件，
     * 这里桥接到 Reactor {@link Sinks}，并在流结束时补发携带停止原因与用量的收尾块。
     */
    @Override
    public Flux<ChatCompletionResponse> streamChat(ChatCompletionRequest request) {
        final String model = request.getModel();
        final Sinks.Many<ChatCompletionResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
        final AtomicBoolean terminated = new AtomicBoolean(false);
        final AtomicReference<StopReason> stopReason = new AtomicReference<>();
        final AtomicReference<TokenUsage> usage = new AtomicReference<>();

        ConverseStreamResponseHandler.Visitor visitor =
                BedrockProtocol.streamVisitor(model, sink::tryEmitNext, stopReason::set, usage::set);

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .subscriber(visitor)
                .onError(error -> terminate(sink, terminated, error, stopReason, usage, model))
                .onComplete(() -> terminate(sink, terminated, null, stopReason, usage, model))
                .build();

        try {
            client.converseStream(BedrockProtocol.buildStreamRequest(model, request), handler)
                    .whenComplete((ignored, error) -> terminate(sink, terminated, error, stopReason, usage, model));
        } catch (RuntimeException e) {
            terminate(sink, terminated, e, stopReason, usage, model);
        }
        return sink.asFlux();
    }

    private static void terminate(
            Sinks.Many<ChatCompletionResponse> sink,
            AtomicBoolean terminated,
            Throwable error,
            AtomicReference<StopReason> stopReason,
            AtomicReference<TokenUsage> usage,
            String model) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        if (error != null) {
            sink.tryEmitError(error);
            return;
        }
        StopReason reason = stopReason.get();
        TokenUsage tokenUsage = usage.get();
        if (reason != null || tokenUsage != null) {
            sink.tryEmitNext(BedrockProtocol.finishChunk(model, reason, tokenUsage));
        }
        sink.tryEmitComplete();
    }

    /**
     * Bedrock 没有廉价的探活端点（Converse 按量计费），因此仅在凭据与 region 齐备时视为可用，
     * 避免误报连接失败。
     */
    @Override
    public Mono<Boolean> validateConnection() {
        return Mono.just(client != null
                && StringUtils.hasText(config.get("region"))
                && StringUtils.hasText(config.get("accessKey")));
    }

    /**
     * 释放底层异步客户端连接池，由 Spring 在容器关闭时回调。
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    private static BedrockRuntimeAsyncClient buildClient(AiProperties.BedrockConfig config) {
        if (!StringUtils.hasText(config.getRegion())
                || !StringUtils.hasText(config.getAccessKey())
                || !StringUtils.hasText(config.getSecretKey())) {
            throw new IllegalArgumentException(
                    "Bedrock provider requires easywing.ai.bedrock.region, access-key and secret-key");
        }

        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(config.getRegion().trim()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        config.getAccessKey().trim(), config.getSecretKey().trim())));
        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint().trim()));
        }
        return builder.build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
