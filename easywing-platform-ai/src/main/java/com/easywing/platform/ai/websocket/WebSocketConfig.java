package com.easywing.platform.ai.websocket;

import com.easywing.platform.ai.config.AiProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final WebSocketTokenInterceptor tokenInterceptor;
    private final AiProperties properties;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                           WebSocketTokenInterceptor tokenInterceptor,
                           AiProperties properties) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.tokenInterceptor = tokenInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .addInterceptors(tokenInterceptor)
                .setAllowedOrigins(properties.getWebsocket().getAllowedOrigins()
                        .toArray(new String[0]));
    }
}
