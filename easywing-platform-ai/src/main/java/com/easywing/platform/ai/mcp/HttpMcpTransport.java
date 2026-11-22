package com.easywing.platform.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 基于 HTTP + SSE 的 MCP 传输实现。
 */
@Slf4j
@Component
public class HttpMcpTransport implements McpTransport {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Session> sessionsByUrl = new ConcurrentHashMap<>();

    public HttpMcpTransport(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return McpServerConfig.TYPE_HTTP;
    }

    @Override
    public Mono<Boolean> initialize(McpServerConfig server) {
        String url = server.getUrl();
        if (sessionsByUrl.containsKey(url)) {
            return Mono.just(true);
        }
        McpRequest request = McpRequest.builder()
                .jsonrpc(McpRequest.JSONRPC_VERSION)
                .id(UUID.randomUUID().toString())
                .method(McpRequest.METHOD_INITIALIZE)
                .params(Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "EasyWingPlatform", "version", "1.0")))
                .build();

        return post(url, server, request, false)
                .flatMap(response -> {
                    if (response.getError() != null || response.getResult() == null) {
                        return Mono.just(false);
                    }
                    return post(
                                    url,
                                    server,
                                    McpRequest.builder()
                                            .jsonrpc(McpRequest.JSONRPC_VERSION)
                                            .method("notifications/initialized")
                                            .build(),
                                    true)
                            .thenReturn(true);
                })
                .timeout(Duration.ofSeconds(server.getTimeoutSeconds()))
                .doOnSuccess(v -> log.info("Connected to MCP server: {}", url))
                .doOnError(e -> log.error("Failed to connect to MCP server: {}", url, e));
    }

    @Override
    public Mono<McpResponse> send(McpServerConfig server, McpRequest request, boolean notification) {
        return initialize(server)
                .flatMap(connected -> connected
                        ? post(server.getUrl(), server, request, notification)
                        : Mono.error(new IllegalStateException("MCP server initialization failed: " + server.getUrl())))
                .timeout(Duration.ofSeconds(server.getTimeoutSeconds()));
    }

    @Override
    public void close(McpServerConfig server) {
        sessionsByUrl.remove(server.getUrl());
    }

    private Mono<McpResponse> post(String serverUrl, McpServerConfig server, McpRequest request, boolean notification) {
        WebClient.RequestBodySpec requestSpec = webClient
                .post()
                .uri(serverUrl)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON);
        String bearerToken = server.getBearerToken();
        if (bearerToken != null && !bearerToken.isBlank()) {
            requestSpec.headers(headers -> headers.setBearerAuth(bearerToken));
        }
        Session session = sessionsByUrl.get(serverUrl);
        if (session != null && session.id != null) {
            requestSpec.header("Mcp-Session-Id", session.id);
        }
        return requestSpec
                .bodyValue(request)
                .exchangeToMono(response -> decode(serverUrl, request, notification, response))
                .doOnError(e -> log.warn("MCP request failed: {}", e.getMessage()));
    }

    private Mono<McpResponse> decode(
            String serverUrl, McpRequest request, boolean notification, ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.createException().flatMap(Mono::error);
        }
        String sessionId =
                response.headers().header("Mcp-Session-Id").stream().findFirst().orElse(null);
        if (request.getMethod() != null && McpRequest.METHOD_INITIALIZE.equals(request.getMethod())) {
            sessionsByUrl.put(serverUrl, new Session(sessionId));
        } else if (sessionId != null) {
            sessionsByUrl.putIfAbsent(serverUrl, new Session(sessionId));
        }
        if (notification || response.statusCode().value() == 202) {
            return response.releaseBody().thenReturn(McpResponse.success(Map.of(), request.getId()));
        }
        MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_JSON);
        return response.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {
            String payload = body;
            if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
                payload = body.lines()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring(5).trim())
                        .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                        .reduce((first, second) -> second)
                        .orElse("");
            }
            if (payload.isBlank()) {
                return Mono.just(McpResponse.success(Map.of(), request.getId()));
            }
            try {
                return Mono.just(objectMapper.readValue(payload, McpResponse.class));
            } catch (Exception ex) {
                return Mono.error(new IllegalStateException("Invalid MCP JSON-RPC response", ex));
            }
        });
    }

    private static class Session {
        final String id;

        Session(String id) {
            this.id = id;
        }
    }
}
