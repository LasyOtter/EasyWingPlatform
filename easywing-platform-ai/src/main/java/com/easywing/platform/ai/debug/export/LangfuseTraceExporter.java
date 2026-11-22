package com.easywing.platform.ai.debug.export;

import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.debug.AgentTrace;
import com.easywing.platform.ai.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Langfuse 链路导出器。
 * <p>
 * 开启方式：{@code easywing.ai.trace.exporter=langfuse}，并配置
 * {@code easywing.ai.langfuse.base-url} / {@code public-key} / {@code secret-key}（仅经配置注入，不硬编码）。
 * 每条终态追踪会向 Langfuse 摄取接口 {@code POST {base-url}/api/public/ingestion} 发送一个
 * {@code trace-create} 事件，{@code Authorization: Basic base64(publicKey:secretKey)}。
 * <p>
 * 密钥缺失时不发送任何请求（导出降级为跳过并打印一次错误日志）；网络异常、超时或非 2xx 响应
 * 只记录告警，不向外抛出，保证主对话流程不受影响。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.trace", name = "exporter", havingValue = "langfuse")
public class LangfuseTraceExporter implements AiTraceExporter {

    private static final String INGESTION_PATH = "/api/public/ingestion";
    private static final int MAX_TEXT_LENGTH = 8000;
    private static final int MAX_BODY_LENGTH = 512;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String ingestionUrl;
    private final String authorization;
    private final int timeoutMs;
    private final boolean enabled;

    public LangfuseTraceExporter(
            @Value("${easywing.ai.langfuse.base-url:}") String baseUrl,
            @Value("${easywing.ai.langfuse.public-key:}") String publicKey,
            @Value("${easywing.ai.langfuse.secret-key:}") String secretKey,
            @Value("${easywing.ai.langfuse.timeout-ms:2000}") int timeoutMs) {
        this.timeoutMs = Math.max(timeoutMs, 1);
        this.ingestionUrl = stripTrailingSlash(baseUrl) + INGESTION_PATH;
        this.authorization = "Basic "
                + Base64.getEncoder().encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        this.enabled = isNotBlank(baseUrl) && isNotBlank(publicKey) && isNotBlank(secretKey);
        if (!enabled) {
            log.error(
                    "已配置 easywing.ai.trace.exporter=langfuse，但 easywing.ai.langfuse.base-url/public-key/secret-key 未完整配置，链路导出将被跳过");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    @Override
    public void export(AgentTrace trace) {
        if (!enabled
                || trace == null
                || trace.getTraceId() == null
                || trace.getTraceId().isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ingestionUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(buildPayload(trace)), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn(
                        "Langfuse 链路导出失败：status={}, traceId={}, body={}",
                        response.statusCode(),
                        trace.getTraceId(),
                        truncate(response.body(), MAX_BODY_LENGTH));
            } else {
                log.debug("Langfuse 链路导出成功：traceId={}", trace.getTraceId());
            }
        } catch (Exception ex) {
            log.warn("Langfuse 链路导出异常（已忽略）：traceId={}, reason={}", trace.getTraceId(), ex.getMessage());
        }
    }

    /**
     * 构建 Langfuse v2 摄取报文：{@code {"batch":[{"type":"trace-create","body":{...}}]}}。
     */
    private Map<String, Object> buildPayload(AgentTrace trace) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", trace.getTraceId());
        body.put("name", "agent:" + nullToEmpty(trace.getAgentId()));
        body.put("sessionId", nullToEmpty(trace.getSessionId()));
        body.put("userId", nullToEmpty(trace.getSessionId()));
        body.put("input", truncate(trace.getUserInput(), MAX_TEXT_LENGTH));
        body.put("output", truncate(extractOutput(trace), MAX_TEXT_LENGTH));
        body.put("metadata", buildMetadata(trace));
        body.put("tags", buildTags(trace));

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        if (trace.getEndTime() > 0) {
            event.put("timestamp", Instant.ofEpochMilli(trace.getEndTime()).toString());
        }
        event.put("type", "trace-create");
        event.put("body", body);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", List.of(event));
        return payload;
    }

    private Map<String, Object> buildMetadata(AgentTrace trace) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("agentId", nullToEmpty(trace.getAgentId()));
        metadata.put("status", trace.getStatus() != null ? trace.getStatus().name() : "UNKNOWN");
        metadata.put("toolStats", trace.getToolStats());
        if (trace.getError() != null && !trace.getError().isBlank()) {
            metadata.put("error", truncate(trace.getError(), MAX_TEXT_LENGTH));
        }

        AgentTrace.PerformanceMetrics metrics = trace.getMetrics();
        if (metrics != null) {
            metadata.put("durationMs", metrics.getTotalDurationMs());
            metadata.put("llmDurationMs", metrics.getLlmDurationMs());
            metadata.put("toolDurationMs", metrics.getToolDurationMs());
            metadata.put("iterationCount", metrics.getIterationCount());
            metadata.put("toolCallCount", metrics.getToolCallCount());
            metadata.put("tokenUsage", metrics.getTokenUsage());
        }
        return metadata;
    }

    private List<String> buildTags(AgentTrace trace) {
        List<String> tags = new ArrayList<>();
        tags.add("agent");
        if (trace.getAgentId() != null && !trace.getAgentId().isBlank()) {
            tags.add(trace.getAgentId());
        }
        if (trace.getStatus() != null) {
            tags.add(trace.getStatus().name().toLowerCase());
        }
        return tags;
    }

    /**
     * 取最终回复文本；失败追踪取错误信息。
     */
    private String extractOutput(AgentTrace trace) {
        if (trace.getError() != null && !trace.getError().isBlank()) {
            return trace.getError();
        }
        AgentResponse response = trace.getResponse();
        if (response == null) {
            return null;
        }
        ChatMessage message = response.getMessage();
        if (message != null && message.getContent() != null) {
            return message.getContent();
        }
        List<ChatMessage> messages = response.getMessages();
        if (messages != null && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (last != null) {
                return last.getContent();
            }
        }
        return null;
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
