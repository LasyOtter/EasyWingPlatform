package com.easywing.platform.ai.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpClient {

    private final WebClient webClient;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public McpClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Boolean> connect(String serverUrl) {
        McpRequest request = McpRequest.builder()
                .jsonrpc("2.0")
                .id("1")
                .method(McpRequest.METHOD_INITIALIZE)
                .build();

        return webClient.post()
                .uri(serverUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(McpResponse.class)
                .map(response -> response.getResult() != null)
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(v -> log.info("Connected to MCP server: {}", serverUrl))
                .doOnError(e -> log.error("Failed to connect to MCP server: {}", serverUrl, e));
    }

    public Mono<McpResponse> sendRequest(String serverUrl, McpRequest request) {
        return webClient.post()
                .uri(serverUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(McpResponse.class)
                .timeout(Duration.ofSeconds(30));
    }

    public void disconnect(String sessionId) {
        sessions.remove(sessionId);
    }

    private static class Session {
        final String id;
        final String serverUrl;

        Session(String id, String serverUrl) {
            this.id = id;
            this.serverUrl = serverUrl;
        }
    }
}