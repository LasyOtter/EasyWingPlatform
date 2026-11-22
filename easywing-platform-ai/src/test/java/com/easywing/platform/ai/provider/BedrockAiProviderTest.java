package com.easywing.platform.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.model.OpenAiTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * AWS Bedrock 提供方测试。
 * <p>
 * 覆盖提供方标识、模型清单回退与覆盖、Converse 请求映射（system / 消息角色 / 工具与工具结果 /
 * inferenceConfig / toolChoice）、响应与流式事件解析，以及流式桥的失败传播。
 * 使用 mock 的 SDK 客户端，不发起真实网络请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class BedrockAiProviderTest {

    private static final String MODEL = "anthropic.claude-3-haiku-20240307-v1:0";

    private static AiProperties.BedrockConfig config(String region, String accessKey, String secretKey) {
        AiProperties.BedrockConfig config = new AiProperties.BedrockConfig();
        config.setEnabled(true);
        config.setRegion(region);
        config.setAccessKey(accessKey);
        config.setSecretKey(secretKey);
        return config;
    }

    private static BedrockAiProvider provider(List<String> models) {
        AiProperties.BedrockConfig config = config("us-east-1", "AKIDEXAMPLE", "SECRETEXAMPLE");
        config.setModels(models);
        return new BedrockAiProvider(
                WebClient.builder().build(), config, Mockito.mock(BedrockRuntimeAsyncClient.class));
    }

    private static ChatCompletionRequest.ChatCompletionRequestBuilder request() {
        return ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(ChatMessage.builder()
                        .role(ChatMessage.ROLE_USER)
                        .content("hi")
                        .build()));
    }

    private static OpenAiTool tool(String name) {
        return OpenAiTool.builder()
                .function(OpenAiTool.FunctionDefinition.builder()
                        .name(name)
                        .description("demo tool")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of("city", Map.of("type", "string")),
                                "required", List.of("city")))
                        .build())
                .build();
    }

    @Test
    void exposesProviderNameAndCapabilities() {
        BedrockAiProvider provider = provider(null);

        assertEquals("bedrock", provider.getProviderName());
        assertTrue(provider.supportsModel(MODEL));
        assertTrue(provider.supportsModel("amazon.titan-text-express-v1"));
        assertFalse(provider.supportsModel("gpt-4o"));

        List<ModelInfo> models = provider.getSupportedModels();
        assertEquals(6, models.size());
        assertTrue(models.stream().allMatch(m -> "bedrock".equals(m.getProvider())));
        assertTrue(models.stream().allMatch(ModelInfo::isSupportsStreaming));
        assertTrue(models.stream().allMatch(ModelInfo::isSupportsTools));
        assertTrue(models.stream().filter(m -> m.getId().contains("claude")).allMatch(ModelInfo::isSupportsVision));
        assertTrue(models.stream().noneMatch(m -> m.getId().contains("titan") && m.isSupportsVision()));
    }

    @Test
    void usesConfiguredModelsWhenProvided() {
        BedrockAiProvider provider = provider(List.of("my-bedrock-model"));

        assertEquals(
                List.of("my-bedrock-model"),
                provider.getSupportedModels().stream().map(ModelInfo::getId).toList());
        assertTrue(provider.supportsModel("my-bedrock-model"));
        assertFalse(provider.supportsModel(MODEL));
    }

    @Test
    void dropsBlankConfiguredModels() {
        BedrockAiProvider provider = provider(List.of(" ", "my-model", ""));

        assertEquals(
                List.of("my-model"),
                provider.getSupportedModels().stream().map(ModelInfo::getId).toList());
    }

    @Test
    void rejectsMissingCredentials() {
        AiProperties.BedrockConfig config = config("us-east-1", "AKIDEXAMPLE", "");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new BedrockAiProvider(WebClient.builder().build(), config));
        assertTrue(exception.getMessage().contains("secret-key"));
    }

    @Test
    void validateConnectionReflectsCredentials() {
        StepVerifier.create(provider(null).validateConnection())
                .expectNext(true)
                .verifyComplete();

        BedrockAiProvider unconfigured = new BedrockAiProvider(
                WebClient.builder().build(),
                config("us-east-1", "", ""),
                Mockito.mock(BedrockRuntimeAsyncClient.class));

        StepVerifier.create(unconfigured.validateConnection()).expectNext(false).verifyComplete();
    }

    @Test
    void chatBuildsConverseRequestAndParsesResponse() {
        BedrockRuntimeAsyncClient client = Mockito.mock(BedrockRuntimeAsyncClient.class);
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        Mockito.when(client.converse(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(ConverseResponse.builder()
                        .output(ConverseOutput.fromMessage(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(
                                        ContentBlock.fromText("sunny"),
                                        ContentBlock.fromToolUse(ToolUseBlock.builder()
                                                .toolUseId("call-1")
                                                .name("get_weather")
                                                .input(Document.fromMap(Map.of("city", Document.fromString("Paris"))))
                                                .build()))
                                .build()))
                        .stopReason(StopReason.TOOL_USE)
                        .usage(TokenUsage.builder()
                                .inputTokens(11)
                                .outputTokens(7)
                                .totalTokens(18)
                                .build())
                        .build()));

        BedrockAiProvider provider = new BedrockAiProvider(
                WebClient.builder().build(), config("us-east-1", "AKIDEXAMPLE", "SECRETEXAMPLE"), client);

        ChatCompletionResponse response = provider.chat(request()
                        .systemPrompt("be terse")
                        .maxTokens(64)
                        .temperature(0.2)
                        .tools(List.of(tool("get_weather")))
                        .toolChoice("auto")
                        .messages(List.of(
                                ChatMessage.builder()
                                        .role(ChatMessage.ROLE_USER)
                                        .content("weather in Paris?")
                                        .build(),
                                ChatMessage.builder()
                                        .role(ChatMessage.ROLE_ASSISTANT)
                                        .content("let me check")
                                        .build(),
                                ChatMessage.builder()
                                        .role(ChatMessage.ROLE_TOOL)
                                        .toolCallId("call-1")
                                        .content("{\"temp\":20}")
                                        .build()))
                        .build())
                .block();

        ConverseRequest awsRequest = captor.getValue();
        assertEquals(MODEL, awsRequest.modelId());
        assertEquals("be terse", awsRequest.system().get(0).text());

        assertEquals(3, awsRequest.messages().size());
        assertEquals(ConversationRole.USER, awsRequest.messages().get(0).role());
        assertEquals(ConversationRole.ASSISTANT, awsRequest.messages().get(1).role());
        assertEquals(ConversationRole.USER, awsRequest.messages().get(2).role());
        ContentBlock toolResult = awsRequest.messages().get(2).content().get(0);
        assertNotNull(toolResult.toolResult());
        assertEquals("call-1", toolResult.toolResult().toolUseId());

        assertEquals(64, awsRequest.inferenceConfig().maxTokens());
        assertEquals(0.2f, awsRequest.inferenceConfig().temperature(), 0.0001f);

        assertEquals(1, awsRequest.toolConfig().tools().size());
        assertEquals(
                "get_weather", awsRequest.toolConfig().tools().get(0).toolSpec().name());
        assertEquals(ToolChoice.Type.AUTO, awsRequest.toolConfig().toolChoice().type());
        Map<?, ?> schema = (Map<?, ?>) awsRequest
                .toolConfig()
                .tools()
                .get(0)
                .toolSpec()
                .inputSchema()
                .json()
                .unwrap();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));

        assertNotNull(response);
        assertEquals("sunny", response.getChoices().get(0).getMessage().getContent());
        assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        assertEquals(1, response.getChoices().get(0).getMessage().getToolCalls().size());
        assertEquals(
                "get_weather",
                response.getChoices()
                        .get(0)
                        .getMessage()
                        .getToolCalls()
                        .get(0)
                        .getFunction()
                        .getName());
        assertEquals(11, response.getUsage().getPromptTokens());
        assertEquals(7, response.getUsage().getCompletionTokens());
        assertEquals(18, response.getUsage().getTotalTokens());
    }

    @Test
    void omitsToolConfigWhenToolChoiceIsNone() {
        ConverseRequest awsRequest = BedrockProtocol.buildRequest(
                MODEL,
                request().tools(List.of(tool("get_weather"))).toolChoice("none").build());

        assertNull(awsRequest.toolConfig());
        assertEquals(1, awsRequest.messages().size());
    }

    @Test
    void mapsToolChoiceVariants() {
        ConverseRequest any = BedrockProtocol.buildRequest(
                MODEL,
                request()
                        .tools(List.of(tool("get_weather")))
                        .toolChoice("required")
                        .build());
        assertEquals(ToolChoice.Type.ANY, any.toolConfig().toolChoice().type());

        ConverseRequest specific = BedrockProtocol.buildRequest(
                MODEL,
                request()
                        .tools(List.of(tool("get_weather")))
                        .toolChoice("get_weather")
                        .build());
        assertEquals(ToolChoice.Type.TOOL, specific.toolConfig().toolChoice().type());
        assertEquals("get_weather", specific.toolConfig().toolChoice().tool().name());
    }

    @Test
    void mapsStopReasons() {
        assertEquals("length", BedrockProtocol.mapStopReason(StopReason.MAX_TOKENS));
        assertEquals("tool_calls", BedrockProtocol.mapStopReason(StopReason.TOOL_USE));
        assertEquals("content_filter", BedrockProtocol.mapStopReason(StopReason.CONTENT_FILTERED));
        assertEquals("content_filter", BedrockProtocol.mapStopReason(StopReason.GUARDRAIL_INTERVENED));
        assertEquals("stop", BedrockProtocol.mapStopReason(StopReason.END_TURN));
        assertEquals("stop", BedrockProtocol.mapStopReason(null));
    }

    @Test
    void streamVisitorTranslatesEvents() {
        List<ChatCompletionResponse> chunks = new ArrayList<>();
        AtomicReference<StopReason> stop = new AtomicReference<>();
        AtomicReference<TokenUsage> usage = new AtomicReference<>();
        ConverseStreamResponseHandler.Visitor visitor =
                BedrockProtocol.streamVisitor(MODEL, chunks::add, stop::set, usage::set);

        visitor.visitContentBlockDelta(ContentBlockDeltaEvent.builder()
                .delta(ContentBlockDelta.fromText("Hel"))
                .build());
        visitor.visitContentBlockDelta(ContentBlockDeltaEvent.builder()
                .delta(ContentBlockDelta.fromText("lo"))
                .build());
        visitor.visitMessageStop(
                MessageStopEvent.builder().stopReason(StopReason.MAX_TOKENS).build());
        visitor.visitMetadata(ConverseStreamMetadataEvent.builder()
                .usage(TokenUsage.builder()
                        .inputTokens(3)
                        .outputTokens(4)
                        .totalTokens(7)
                        .build())
                .build());

        assertEquals(2, chunks.size());
        assertEquals(
                "Hello",
                chunks.get(0).getChoices().get(0).getDelta().getContent()
                        + chunks.get(1).getChoices().get(0).getDelta().getContent());
        assertEquals("chat.completion.chunk", chunks.get(0).getObject());
        assertEquals(MODEL, chunks.get(0).getModel());
        assertEquals(StopReason.MAX_TOKENS, stop.get());
        assertEquals(7, usage.get().totalTokens());
    }

    @Test
    void finishChunkCarriesStopReasonAndUsage() {
        ChatCompletionResponse chunk = BedrockProtocol.finishChunk(
                MODEL,
                StopReason.MAX_TOKENS,
                TokenUsage.builder().inputTokens(5).outputTokens(6).build());

        assertEquals("length", chunk.getChoices().get(0).getFinishReason());
        assertEquals(5, chunk.getUsage().getPromptTokens());
        assertEquals(6, chunk.getUsage().getCompletionTokens());
        assertEquals(11, chunk.getUsage().getTotalTokens());
        assertNull(chunk.getChoices().get(0).getDelta());
    }

    @Test
    void streamChatPropagatesClientFailure() {
        BedrockRuntimeAsyncClient client = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converseStream(
                        Mockito.any(ConverseStreamRequest.class), Mockito.any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
        BedrockAiProvider provider = new BedrockAiProvider(
                WebClient.builder().build(), config("us-east-1", "AKIDEXAMPLE", "SECRETEXAMPLE"), client);

        StepVerifier.create(provider.streamChat(request().build()))
                .expectErrorMatches(BedrockAiProviderTest::mentionsBoom)
                .verify();
    }

    @Test
    void streamChatCompletesWithoutFinishChunkWhenNoMetadata() {
        BedrockRuntimeAsyncClient client = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converseStream(
                        Mockito.any(ConverseStreamRequest.class), Mockito.any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        BedrockAiProvider provider = new BedrockAiProvider(
                WebClient.builder().build(), config("us-east-1", "AKIDEXAMPLE", "SECRETEXAMPLE"), client);

        List<ChatCompletionResponse> chunks =
                provider.streamChat(request().build()).collectList().block();

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    private static boolean mentionsBoom(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("boom")) {
                return true;
            }
        }
        return false;
    }
}
