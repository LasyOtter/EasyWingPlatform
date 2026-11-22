package com.easywing.platform.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 基于本地子进程 stdin/stdout 的 MCP 传输实现（JSON-RPC 2.0，换行分隔）。
 * <p>
 * 进程 I/O 是阻塞式的，因此整体在 {@link Schedulers#boundedElastic()} 上执行，避免阻塞事件循环。
 */
@Slf4j
@Component
public class StdioMcpTransport implements McpTransport {

    private static final long POLL_INTERVAL_MILLIS = 5L;

    private final ObjectMapper objectMapper;
    private final StdioProcessLauncher processLauncher;
    private final ConcurrentHashMap<String, StdioSession> sessions = new ConcurrentHashMap<>();

    public StdioMcpTransport(ObjectMapper objectMapper, StdioProcessLauncher processLauncher) {
        this.objectMapper = objectMapper;
        this.processLauncher = processLauncher;
    }

    @Override
    public String type() {
        return McpServerConfig.TYPE_STDIO;
    }

    @Override
    public Mono<Boolean> initialize(McpServerConfig server) {
        return Mono.fromCallable(() -> doInitialize(server))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to initialize MCP stdio server: {}", server.getCommand(), e));
    }

    @Override
    public Mono<McpResponse> send(McpServerConfig server, McpRequest request, boolean notification) {
        return Mono.fromCallable(() -> {
                    StdioSession session = sessionFor(server);
                    synchronized (session) {
                        if (notification) {
                            write(session, request);
                            return McpResponse.success(Map.of(), request.getId());
                        }
                        return exchange(session, request, server.getTimeoutSeconds());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public void close(McpServerConfig server) {
        StdioSession session = sessions.remove(key(server));
        if (session != null) {
            session.close();
        }
    }

    private boolean doInitialize(McpServerConfig server) throws Exception {
        String key = key(server);
        StdioSession existing = sessions.get(key);
        if (existing != null && existing.isAlive()) {
            return true;
        }
        StdioSession session = startSession(server);
        try {
            synchronized (session) {
                McpResponse response = exchange(
                        session,
                        McpRequest.builder()
                                .jsonrpc(McpRequest.JSONRPC_VERSION)
                                .id(UUID.randomUUID().toString())
                                .method(McpRequest.METHOD_INITIALIZE)
                                .params(Map.of(
                                        "protocolVersion", "2024-11-05",
                                        "capabilities", Map.of(),
                                        "clientInfo", Map.of("name", "EasyWingPlatform", "version", "1.0")))
                                .build(),
                        server.getTimeoutSeconds());
                if (response.getError() != null || response.getResult() == null) {
                    throw new IllegalStateException("MCP stdio 初始化失败: " + server.getCommand());
                }
                write(
                        session,
                        McpRequest.builder()
                                .jsonrpc(McpRequest.JSONRPC_VERSION)
                                .method("notifications/initialized")
                                .build());
            }
            sessions.put(key, session);
            log.info("Connected to MCP stdio server: {}", server.getCommand());
            return true;
        } catch (Exception e) {
            session.close();
            throw e;
        }
    }

    private StdioSession sessionFor(McpServerConfig server) throws Exception {
        String key = key(server);
        StdioSession session = sessions.get(key);
        if (session != null && session.isAlive()) {
            return session;
        }
        doInitialize(server);
        StdioSession initialized = sessions.get(key);
        if (initialized == null) {
            throw new IllegalStateException("MCP stdio server 未初始化: " + server.getCommand());
        }
        return initialized;
    }

    private McpResponse exchange(StdioSession session, McpRequest request, int timeoutSeconds) throws Exception {
        write(session, request);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (true) {
            String line = readLine(session, deadline);
            if (line == null) {
                throw new IllegalStateException("MCP stdio server 在超时前未返回响应");
            }
            if (line.isBlank()) {
                continue;
            }
            McpResponse response;
            try {
                response = objectMapper.readValue(line, McpResponse.class);
            } catch (Exception ex) {
                log.debug("忽略 MCP stdio 非协议输出: {}", line);
                continue;
            }
            if (request.getId() != null && !request.getId().equals(response.getId())) {
                continue;
            }
            return response;
        }
    }

    /**
     * 带截止时间的按行读取：通过 available() 轮询，避免阻塞读导致超时无法生效。
     */
    private String readLine(StdioSession session, long deadline) throws Exception {
        InputStream in = session.process.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (System.currentTimeMillis() < deadline) {
            if (in.available() > 0) {
                int b = in.read();
                if (b < 0) {
                    return buffer.size() == 0
                            ? null
                            : buffer.toString(StandardCharsets.UTF_8).trim();
                }
                if (b == '\n') {
                    String line = buffer.toString(StandardCharsets.UTF_8).trim();
                    buffer.reset();
                    if (!line.isEmpty()) {
                        return line;
                    }
                } else if (b != '\r') {
                    buffer.write(b);
                }
            } else if (!session.isAlive() && in.available() == 0) {
                return buffer.size() == 0
                        ? null
                        : buffer.toString(StandardCharsets.UTF_8).trim();
            } else {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        }
        throw new IOException("MCP stdio 读取超时");
    }

    private void write(StdioSession session, McpRequest request) throws IOException {
        OutputStream out = session.process.getOutputStream();
        out.write(objectMapper.writeValueAsBytes(request));
        out.write('\n');
        out.flush();
    }

    private StdioSession startSession(McpServerConfig server) throws IOException {
        StdioSession session = new StdioSession(processLauncher.start(server));
        Thread stderrReader = new Thread(() -> drainStderr(session), "mcp-stdio-stderr-" + key(server));
        stderrReader.setDaemon(true);
        stderrReader.start();
        session.stderrReader = stderrReader;
        return session;
    }

    private void drainStderr(StdioSession session) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(session.process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("MCP stdio stderr: {}", line);
            }
        } catch (IOException e) {
            log.debug("MCP stdio stderr 已关闭: {}", e.getMessage());
        }
    }

    private String key(McpServerConfig server) {
        List<String> args = server.getArgs() == null ? List.of() : server.getArgs();
        return server.getCommand() + " " + String.join(" ", args);
    }

    private static final class StdioSession {
        private final Process process;
        private Thread stderrReader;

        private StdioSession(Process process) {
            this.process = process;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void close() {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            if (stderrReader != null) {
                stderrReader.interrupt();
            }
        }
    }
}
