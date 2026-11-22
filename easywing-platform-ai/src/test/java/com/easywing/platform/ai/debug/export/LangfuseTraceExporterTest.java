package com.easywing.platform.ai.debug.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.easywing.platform.ai.agent.AgentResponse;
import com.easywing.platform.ai.debug.AgentTrace;
import com.easywing.platform.ai.model.ChatMessage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LangfuseTraceExporterTest {

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/ingestion", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestMethod.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private LangfuseTraceExporter newExporter(String publicKey, String secretKey) {
        return new LangfuseTraceExporter(
                "http://127.0.0.1:" + server.getAddress().getPort(), publicKey, secretKey, 2000);
    }

    @Test
    void sendsTraceCreateEventWithBasicAuth() {
        newExporter("pk_test", "sk_test").export(completedTrace());

        assertThat(requestPath.get()).isEqualTo("/api/public/ingestion");
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(authorization.get())
                .isEqualTo("Basic "
                        + Base64.getEncoder().encodeToString("pk_test:sk_test".getBytes(StandardCharsets.UTF_8)));

        String body = requestBody.get();
        assertThat(body).contains("\"type\":\"trace-create\"");
        assertThat(body).contains("\"id\":\"trace-1\"");
        assertThat(body).contains("\"sessionId\":\"session-1\"");
        assertThat(body).contains("\"input\":\"你好\"");
        assertThat(body).contains("\"output\":\"你好，我是助手\"");
        assertThat(body).contains("\"tokenUsage\":34");
    }

    @Test
    void failedTraceUsesErrorAsOutput() {
        AgentTrace failed = AgentTrace.builder()
                .traceId("trace-2")
                .agentId("agent-1")
                .sessionId("session-1")
                .userInput("hi")
                .endTime(1L)
                .status(AgentTrace.ExecutionStatus.FAILED)
                .error("provider timeout")
                .build();

        newExporter("pk", "sk").export(failed);

        assertThat(requestBody.get()).contains("\"output\":\"provider timeout\"");
        assertThat(requestBody.get()).contains("\"status\":\"FAILED\"");
    }

    @Test
    void non2xxResponseIsSwallowed() {
        responseStatus.set(500);

        assertThatCode(() -> newExporter("pk", "sk").export(completedTrace())).doesNotThrowAnyException();
        assertThat(requestPath.get()).isEqualTo("/api/public/ingestion");
    }

    @Test
    void missingCredentialsSkipsExport() {
        newExporter("", "sk").export(completedTrace());

        assertThat(requestPath.get()).isNull();
    }

    private AgentTrace completedTrace() {
        return AgentTrace.builder()
                .traceId("trace-1")
                .agentId("agent-1")
                .sessionId("session-1")
                .userInput("你好")
                .endTime(1_700_000_000_000L)
                .status(AgentTrace.ExecutionStatus.COMPLETED)
                .response(AgentResponse.builder()
                        .message(ChatMessage.builder()
                                .role("assistant")
                                .content("你好，我是助手")
                                .build())
                        .build())
                .metrics(AgentTrace.PerformanceMetrics.builder()
                        .totalDurationMs(120L)
                        .tokenUsage(34)
                        .iterationCount(2)
                        .toolCallCount(1)
                        .build())
                .build();
    }
}
