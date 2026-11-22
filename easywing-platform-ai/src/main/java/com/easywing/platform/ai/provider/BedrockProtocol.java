package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.OpenAiTool;
import com.easywing.platform.ai.model.OpenAiToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.AnyToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.AutoToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Bedrock Converse 线协议适配逻辑。
 * <p>
 * AWS Bedrock 的 Converse / ConverseStream 使用一套统一的消息结构
 * （{@code messages[]} + {@code system[]} + {@code inferenceConfig} + {@code toolConfig}），
 * 与 OpenAI Chat Completions 的角色名、工具定义与响应字段均不同，这里集中转换，
 * 避免把 AWS SDK 类型泄漏到调用方。
 * <p>
 * 注意：{@code tool} 角色的消息需要包在 {@code user} 消息的 {@code toolResult} 块中回传，
 * 否则 Bedrock 会拒绝第二轮工具调用请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
final class BedrockProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BedrockProtocol() {}

    static ConverseRequest buildRequest(String modelId, ChatCompletionRequest request) {
        ConversePayload payload = payload(request);
        ConverseRequest.Builder builder = ConverseRequest.builder().modelId(modelId);
        if (!payload.messages().isEmpty()) {
            builder.messages(payload.messages());
        }
        if (!payload.system().isEmpty()) {
            builder.system(payload.system());
        }
        if (payload.inferenceConfig() != null) {
            builder.inferenceConfig(payload.inferenceConfig());
        }
        if (payload.toolConfig() != null) {
            builder.toolConfig(payload.toolConfig());
        }
        return builder.build();
    }

    static ConverseStreamRequest buildStreamRequest(String modelId, ChatCompletionRequest request) {
        ConversePayload payload = payload(request);
        ConverseStreamRequest.Builder builder = ConverseStreamRequest.builder().modelId(modelId);
        if (!payload.messages().isEmpty()) {
            builder.messages(payload.messages());
        }
        if (!payload.system().isEmpty()) {
            builder.system(payload.system());
        }
        if (payload.inferenceConfig() != null) {
            builder.inferenceConfig(payload.inferenceConfig());
        }
        if (payload.toolConfig() != null) {
            builder.toolConfig(payload.toolConfig());
        }
        return builder.build();
    }

    /**
     * 解析非流式 Converse 响应。
     */
    static ChatCompletionResponse parse(ConverseResponse response, String modelId) {
        StringBuilder content = new StringBuilder();
        List<OpenAiToolCall> toolCalls = new ArrayList<>();

        if (response.output() != null && response.output().message() != null) {
            Message message = response.output().message();
            if (message.content() != null) {
                for (ContentBlock block : message.content()) {
                    if (block.text() != null) {
                        content.append(block.text());
                    }
                    if (block.toolUse() != null) {
                        toolCalls.add(toToolCall(block.toolUse()));
                    }
                }
            }
        }

        return ChatCompletionResponse.builder()
                .id("bedrock-" + Instant.now().getEpochSecond())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(modelId)
                .choices(List.of(ChatCompletionResponse.Choice.builder()
                        .index(0)
                        .message(ChatMessage.builder()
                                .role(ChatMessage.ROLE_ASSISTANT)
                                .content(content.toString())
                                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                                .build())
                        .finishReason(mapStopReason(response.stopReason()))
                        .build()))
                .usage(toUsage(response.usage()))
                .build();
    }

    /**
     * 流式文本增量块。
     */
    static ChatCompletionResponse deltaChunk(String text, String modelId) {
        return chunk(
                modelId,
                ChatMessage.builder()
                        .role(ChatMessage.ROLE_ASSISTANT)
                        .content(text)
                        .build(),
                null,
                null);
    }

    /**
     * 流式结束块，携带停止原因与 token 用量。
     */
    static ChatCompletionResponse finishChunk(String modelId, StopReason reason, TokenUsage usage) {
        return chunk(modelId, null, mapStopReason(reason), toUsage(usage));
    }

    /**
     * 构造流式事件回调：把 SDK 的 Visitor 事件翻译成平台响应块与元数据回调，
     * 使事件映射可脱离真实网络调用单独验证。
     */
    static ConverseStreamResponseHandler.Visitor streamVisitor(
            String modelId,
            Consumer<ChatCompletionResponse> onChunk,
            Consumer<StopReason> onStopReason,
            Consumer<TokenUsage> onUsage) {
        return ConverseStreamResponseHandler.Visitor.builder()
                .onContentBlockDelta(event -> {
                    ContentBlockDelta delta = event.delta();
                    if (delta != null && delta.text() != null) {
                        onChunk.accept(deltaChunk(delta.text(), modelId));
                    }
                })
                .onMessageStop(event -> onStopReason.accept(event.stopReason()))
                .onMetadata(event -> onUsage.accept(event.usage()))
                .build();
    }

    /**
     * Bedrock {@code stopReason} 到 OpenAI {@code finish_reason} 的语义映射。
     */
    static String mapStopReason(StopReason reason) {
        if (reason == null) {
            return "stop";
        }
        return switch (reason) {
            case MAX_TOKENS -> "length";
            case TOOL_USE -> "tool_calls";
            case CONTENT_FILTERED, GUARDRAIL_INTERVENED -> "content_filter";
            default -> "stop";
        };
    }

    private static ChatCompletionResponse chunk(
            String modelId, ChatMessage delta, String finishReason, ChatCompletionResponse.Usage usage) {
        return ChatCompletionResponse.builder()
                .id("bedrock-" + Instant.now().getEpochSecond())
                .object("chat.completion.chunk")
                .created(Instant.now().getEpochSecond())
                .model(modelId)
                .choices(List.of(ChatCompletionResponse.Choice.builder()
                        .index(0)
                        .delta(delta)
                        .finishReason(finishReason)
                        .build()))
                .usage(usage)
                .build();
    }

    private static ConversePayload payload(ChatCompletionRequest request) {
        List<SystemContentBlock> system = new ArrayList<>();
        addSystem(system, request.getSystemPrompt());

        List<Message> messages = new ArrayList<>();
        List<ContentBlock> toolResults = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                String role = message.getRole() == null ? ChatMessage.ROLE_USER : message.getRole();
                if (ChatMessage.ROLE_SYSTEM.equals(role)) {
                    addSystem(system, message.getContent());
                    continue;
                }
                if (ChatMessage.ROLE_TOOL.equals(role)) {
                    toolResults.add(toolResultBlock(message));
                    continue;
                }
                flushToolResults(messages, toolResults);
                ConversationRole conversationRole =
                        ChatMessage.ROLE_ASSISTANT.equals(role) ? ConversationRole.ASSISTANT : ConversationRole.USER;
                List<ContentBlock> blocks = new ArrayList<>();
                if (message.getContent() != null && !message.getContent().isBlank()) {
                    blocks.add(ContentBlock.fromText(message.getContent()));
                }
                if (!blocks.isEmpty()) {
                    messages.add(Message.builder()
                            .role(conversationRole)
                            .content(blocks)
                            .build());
                }
            }
        }
        flushToolResults(messages, toolResults);

        return new ConversePayload(messages, system, inferenceConfig(request), toolConfig(request));
    }

    private static void addSystem(List<SystemContentBlock> system, String text) {
        if (text != null && !text.isBlank()) {
            system.add(SystemContentBlock.builder().text(text).build());
        }
    }

    /**
     * Bedrock 要求工具结果作为 {@code user} 消息中的 {@code toolResult} 块回传，
     * 连续的 tool 消息合并为同一条 user 消息。
     */
    private static void flushToolResults(List<Message> messages, List<ContentBlock> toolResults) {
        if (toolResults.isEmpty()) {
            return;
        }
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(List.copyOf(toolResults))
                .build());
        toolResults.clear();
    }

    private static ContentBlock toolResultBlock(ChatMessage message) {
        ToolResultContentBlock content = ToolResultContentBlock.builder()
                .text(message.getContent() == null ? "" : message.getContent())
                .build();
        return ContentBlock.fromToolResult(ToolResultBlock.builder()
                .toolUseId(message.getToolCallId() == null ? "" : message.getToolCallId())
                .content(content)
                .build());
    }

    private static InferenceConfiguration inferenceConfig(ChatCompletionRequest request) {
        InferenceConfiguration.Builder builder = InferenceConfiguration.builder();
        boolean configured = false;

        int maxTokens =
                request.getMaxCompletionTokens() > 0 ? request.getMaxCompletionTokens() : request.getMaxTokens();
        if (maxTokens > 0) {
            builder.maxTokens(maxTokens);
            configured = true;
        }
        if (request.getTemperature() > 0) {
            builder.temperature((float) request.getTemperature());
            configured = true;
        }
        if (request.getTopP() > 0) {
            builder.topP((float) request.getTopP());
            configured = true;
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            builder.stopSequences(request.getStop());
            configured = true;
        }
        return configured ? builder.build() : null;
    }

    private static ToolConfiguration toolConfig(ChatCompletionRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return null;
        }
        if ("none".equalsIgnoreCase(request.getToolChoice())) {
            return null;
        }

        List<Tool> tools = new ArrayList<>();
        for (OpenAiTool tool : request.getTools()) {
            if (tool == null || tool.getFunction() == null) {
                continue;
            }
            OpenAiTool.FunctionDefinition function = tool.getFunction();
            if (function.getName() == null || function.getName().isBlank()) {
                continue;
            }
            ToolSpecification.Builder specification =
                    ToolSpecification.builder().name(function.getName());
            if (function.getDescription() != null && !function.getDescription().isBlank()) {
                specification.description(function.getDescription());
            }
            specification.inputSchema(ToolInputSchema.builder()
                    .json(function.getParameters() == null ? emptyObjectSchema() : toDocument(function.getParameters()))
                    .build());
            tools.add(Tool.builder().toolSpec(specification.build()).build());
        }
        if (tools.isEmpty()) {
            return null;
        }

        ToolConfiguration.Builder builder = ToolConfiguration.builder().tools(tools);
        ToolChoice choice = toolChoice(request.getToolChoice());
        if (choice != null) {
            builder.toolChoice(choice);
        }
        return builder.build();
    }

    private static ToolChoice toolChoice(String requested) {
        if (requested == null || requested.isBlank() || "auto".equalsIgnoreCase(requested)) {
            return ToolChoice.fromAuto(AutoToolChoice.builder().build());
        }
        if ("required".equalsIgnoreCase(requested) || "any".equalsIgnoreCase(requested)) {
            return ToolChoice.fromAny(AnyToolChoice.builder().build());
        }
        return ToolChoice.fromTool(SpecificToolChoice.builder().name(requested).build());
    }

    private static Document emptyObjectSchema() {
        Map<String, Document> schema = new LinkedHashMap<>();
        schema.put("type", Document.fromString("object"));
        schema.put("properties", Document.fromMap(new LinkedHashMap<String, Document>()));
        return Document.fromMap(schema);
    }

    /**
     * 把 JSON Schema 对象（Map/List/标量）递归转换为 SDK 的 {@link Document}。
     */
    private static Document toDocument(Object value) {
        if (value == null) {
            return Document.fromNull();
        }
        if (value instanceof Boolean bool) {
            return Document.fromBoolean(bool);
        }
        if (value instanceof Number number) {
            return Document.fromNumber(number.toString());
        }
        if (value instanceof String text) {
            return Document.fromString(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Document> converted = new LinkedHashMap<>();
            map.forEach((key, entry) -> converted.put(String.valueOf(key), toDocument(entry)));
            return Document.fromMap(converted);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Document> converted = new ArrayList<>();
            for (Object entry : iterable) {
                converted.add(toDocument(entry));
            }
            return Document.fromList(converted);
        }
        return Document.fromString(value.toString());
    }

    private static OpenAiToolCall toToolCall(ToolUseBlock toolUse) {
        return OpenAiToolCall.builder()
                .id(toolUse.toolUseId())
                .type("function")
                .function(OpenAiToolCall.FunctionCall.builder()
                        .name(toolUse.name())
                        .arguments(toJson(toolUse.input()))
                        .build())
                .build();
    }

    private static String toJson(Document input) {
        if (input == null || input.isNull()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(input.unwrap());
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ChatCompletionResponse.Usage toUsage(TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        int inputTokens = usage.inputTokens() == null ? 0 : usage.inputTokens();
        int outputTokens = usage.outputTokens() == null ? 0 : usage.outputTokens();
        int totalTokens = usage.totalTokens() == null ? inputTokens + outputTokens : usage.totalTokens();
        return ChatCompletionResponse.Usage.builder()
                .promptTokens(inputTokens)
                .completionTokens(outputTokens)
                .totalTokens(totalTokens)
                .build();
    }

    private record ConversePayload(
            List<Message> messages,
            List<SystemContentBlock> system,
            InferenceConfiguration inferenceConfig,
            ToolConfiguration toolConfig) {}
}
