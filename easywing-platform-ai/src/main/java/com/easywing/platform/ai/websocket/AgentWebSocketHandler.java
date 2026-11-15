package com.easywing.platform.ai.websocket;

import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.agent.AgentStreamEvent;
import com.easywing.platform.ai.agent.StreamingReactAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Agent 处理器 - 支持双向通信
 */
@Slf4j
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final StreamingReactAgent streamingReactAgent;
    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public AgentWebSocketHandler(StreamingReactAgent streamingReactAgent, ObjectMapper objectMapper) {
        this.streamingReactAgent = streamingReactAgent;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("WebSocket connection established: {}", sessionId);

        // 发送欢迎消息
        WebSocketMessage welcomeMsg = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.CONNECTED)
                .data(Map.of(
                        "sessionId", sessionId,
                        "message", "Connected to EasyWing AI Agent",
                        "timestamp", System.currentTimeMillis()
                ))
                .build();
        sendMessage(session, welcomeMsg);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        log.info("Received message from {}: {}", sessionId, message.getPayload());

        try {
            WebSocketMessage msg = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

            switch (msg.getType()) {
                case EXECUTE -> handleExecute(session, msg);
                case CANCEL -> handleCancel(session, msg);
                case PING -> handlePing(session, msg);
                case CONFIGURE -> handleConfigure(session, msg);
                default -> {
                    log.warn("Unknown message type: {}", msg.getType());
                    sendError(session, "Unknown message type: " + msg.getType());
                }
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    /**
     * 处理执行请求
     */
    private void handleExecute(WebSocketSession session, WebSocketMessage msg) {
        String sessionId = session.getId();

        // 取消之前的执行
        cancelExecution(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.getData();
            String prompt = (String) data.get("prompt");
            String agentId = (String) data.getOrDefault("agentId", "default");
            String model = (String) data.getOrDefault("model", "gpt-4o");
            Integer maxIterations = (Integer) data.getOrDefault("maxIterations", 10);

            AgentRequest request = AgentRequest.builder()
                    .agentId(agentId)
                    .prompt(prompt)
                    .sessionId(sessionId)
                    .config(AgentRequest.AgentConfig.builder()
                            .model(model)
                            .maxIterations(maxIterations)
                            .maxToolCalls(20)
                            .temperature(0.7)
                            .build())
                    .build();

            // 订阅流式执行
            Disposable disposable = streamingReactAgent.executeStream(request)
                    .subscribe(
                            event -> sendAgentEvent(session, event),
                            error -> {
                                log.error("Agent execution error", error);
                                sendError(session, "Execution error: " + error.getMessage());
                            },
                            () -> log.info("Agent execution completed for session: {}", sessionId)
                    );

            activeExecutions.put(sessionId, disposable);
            log.info("Started agent execution for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Failed to start execution", e);
            sendError(session, "Failed to start execution: " + e.getMessage());
        }
    }

    /**
     * 处理取消请求
     */
    private void handleCancel(WebSocketSession session, WebSocketMessage msg) {
        String sessionId = session.getId();
        cancelExecution(sessionId);

        WebSocketMessage response = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.CANCELLED)
                .data(Map.of("message", "Execution cancelled"))
                .build();
        sendMessage(session, response);
    }

    /**
     * 处理心跳
     */
    private void handlePing(WebSocketSession session, WebSocketMessage msg) {
        WebSocketMessage pong = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.PONG)
                .data(Map.of("timestamp", System.currentTimeMillis()))
                .build();
        sendMessage(session, pong);
    }

    /**
     * 处理配置更新
     */
    private void handleConfigure(WebSocketSession session, WebSocketMessage msg) {
        // 可以在这里处理会话配置更新
        WebSocketMessage response = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.CONFIGURED)
                .data(Map.of("message", "Configuration updated"))
                .build();
        sendMessage(session, response);
    }

    /**
     * 发送 Agent 事件
     */
    private void sendAgentEvent(WebSocketSession session, AgentStreamEvent event) {
        WebSocketMessage msg = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.AGENT_EVENT)
                .data(event)
                .build();
        sendMessage(session, msg);
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String error) {
        WebSocketMessage msg = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.ERROR)
                .data(Map.of("error", error))
                .build();
        sendMessage(session, msg);
    }

    /**
     * 发送消息
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send message", e);
        }
    }

    /**
     * 取消执行
     */
    private void cancelExecution(String sessionId) {
        Disposable disposable = activeExecutions.remove(sessionId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.info("Cancelled execution for session: {}", sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        cancelExecution(sessionId);
        log.info("WebSocket connection closed: {}, status: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", session.getId(), exception);
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        cancelExecution(sessionId);
    }
}
