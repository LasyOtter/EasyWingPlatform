package com.easywing.platform.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StdioMcpTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void typeIsStdio() {
        assertThat(transport(new FakeProcess(objectMapper)).type()).isEqualTo(McpServerConfig.TYPE_STDIO);
    }

    @Test
    void initializePerformsProtocolHandshake() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "initialize".equals(request.path("method").asText())
                        ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                        : List.of();

        assertThat(transport(process).initialize(config()).block()).isTrue();

        assertThat(process.messages).hasSize(2);
        assertThat(process.messages.get(0).path("method").asText()).isEqualTo("initialize");
        assertThat(process.messages
                        .get(0)
                        .path("params")
                        .path("protocolVersion")
                        .asText())
                .isEqualTo("2024-11-05");
        assertThat(process.messages.get(1).path("method").asText()).isEqualTo("notifications/initialized");
    }

    @Test
    void initializeIsIdempotentForSameServer() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "initialize".equals(request.path("method").asText())
                        ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                        : List.of();
        StdioMcpTransport transport = transport(process);

        assertThat(transport.initialize(config()).block()).isTrue();
        assertThat(transport.initialize(config()).block()).isTrue();

        assertThat(process.messages).hasSize(2);
    }

    @Test
    void sendReturnsToolListResult() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder = request -> switch (request.path("method").asText()) {
            case "initialize" -> List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")));
            case "tools/list" -> List.of(jsonResponse(
                    request, Map.of("tools", List.of(Map.of("name", "echo", "description", "Echo input")))));
            default -> List.of();
        };
        StdioMcpTransport transport = transport(process);
        assertThat(transport.initialize(config()).block()).isTrue();

        McpResponse response =
                transport.send(config(), toolsListRequest(), false).block();

        assertThat(response).isNotNull();
        assertThat(response.getError()).isNull();
        assertThat(response.getResult()).isInstanceOf(Map.class);
        assertThat(process.messages)
                .anyMatch(message -> "tools/list".equals(message.path("method").asText()));
    }

    @Test
    void sendSkipsNonProtocolOutputLines() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "tools/list".equals(request.path("method").asText())
                        ? List.of("child process log line", jsonResponse(request, Map.of("tools", List.of())))
                        : "initialize".equals(request.path("method").asText())
                                ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                                : List.of();
        StdioMcpTransport transport = transport(process);
        assertThat(transport.initialize(config()).block()).isTrue();

        McpResponse response =
                transport.send(config(), toolsListRequest(), false).block();

        assertThat(response).isNotNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void notificationDoesNotWaitForResponse() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "initialize".equals(request.path("method").asText())
                        ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                        : List.of();
        StdioMcpTransport transport = transport(process);
        assertThat(transport.initialize(config()).block()).isTrue();

        McpResponse response = transport
                .send(
                        config(),
                        McpRequest.builder()
                                .jsonrpc(McpRequest.JSONRPC_VERSION)
                                .method("notifications/cancelled")
                                .build(),
                        true)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getError()).isNull();
        // initialize 产生 initialize + notifications/initialized 两条，本次通知再追加一条且不等待响应
        assertThat(process.messages).hasSize(3);
    }

    @Test
    void sendTimesOutWhenServerDoesNotRespond() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "initialize".equals(request.path("method").asText())
                        ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                        : List.of();
        StdioMcpTransport transport = transport(process);
        assertThat(transport.initialize(config()).block()).isTrue();

        assertThatThrownBy(() ->
                        transport.send(config(), toolsListRequest(), false).block())
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    void closeDestroysProcess() {
        FakeProcess process = new FakeProcess(objectMapper);
        process.responder =
                request -> "initialize".equals(request.path("method").asText())
                        ? List.of(jsonResponse(request, Map.of("protocolVersion", "2024-11-05")))
                        : List.of();
        StdioMcpTransport transport = transport(process);
        assertThat(transport.initialize(config()).block()).isTrue();

        transport.close(config());

        assertThat(process.destroyed).isTrue();
        assertThat(process.isAlive()).isFalse();
    }

    private StdioMcpTransport transport(FakeProcess process) {
        return new StdioMcpTransport(objectMapper, server -> process);
    }

    private McpServerConfig config() {
        return McpServerConfig.builder()
                .name("fake")
                .type(McpServerConfig.TYPE_STDIO)
                .command("fake-mcp-server")
                .timeoutSeconds(1)
                .build();
    }

    private McpRequest toolsListRequest() {
        return McpRequest.builder()
                .jsonrpc(McpRequest.JSONRPC_VERSION)
                .id("tools-list-1")
                .method(McpRequest.METHOD_TOOLS_LIST)
                .params(Map.of())
                .build();
    }

    private String jsonResponse(JsonNode request, Map<String, Object> result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", request.path("id").asText());
        response.set("result", objectMapper.valueToTree(result));
        return response.toString();
    }

    private interface Responder {
        List<String> respond(JsonNode request);
    }

    private static final class FakeProcess extends Process {

        private final ObjectMapper objectMapper;
        private final QueueInputStream stdout = new QueueInputStream();
        private final List<JsonNode> messages = new CopyOnWriteArrayList<>();
        private Responder responder = request -> List.of();
        private volatile boolean alive = true;
        private volatile boolean destroyed;

        private FakeProcess(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {
                private final ByteArrayOutputStream line = new ByteArrayOutputStream();

                @Override
                public void write(int b) throws IOException {
                    if (b == '\n') {
                        String text = line.toString(StandardCharsets.UTF_8);
                        line.reset();
                        if (!text.isBlank()) {
                            handle(text);
                        }
                    } else {
                        line.write(b);
                    }
                }
            };
        }

        private void handle(String json) {
            try {
                JsonNode request = objectMapper.readTree(json);
                messages.add(request);
                responder.respond(request).forEach(stdout::offer);
            } catch (Exception e) {
                throw new IllegalStateException("假 MCP 进程无法解析请求: " + json, e);
            }
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return new InputStream() {
                @Override
                public int read() {
                    return -1;
                }
            };
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void destroy() {
            alive = false;
            destroyed = true;
            stdout.close();
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }
    }

    private static final class QueueInputStream extends InputStream {

        private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        private byte[] current = new byte[0];
        private int position;
        private volatile boolean closed;

        private void offer(String line) {
            chunks.add((line + "\n").getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public synchronized int read() throws IOException {
            while (position >= current.length) {
                byte[] next;
                try {
                    next = chunks.poll(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("读取被中断", e);
                }
                if (next == null) {
                    if (closed) {
                        return -1;
                    }
                    continue;
                }
                current = next;
                position = 0;
            }
            return current[position++] & 0xFF;
        }

        @Override
        public synchronized int available() {
            if (position < current.length) {
                return current.length - position;
            }
            return chunks.isEmpty() ? 0 : 1;
        }
    }
}
