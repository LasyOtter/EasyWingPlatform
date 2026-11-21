package com.easywing.platform.ai.websocket;

import com.easywing.platform.ai.config.AiProperties;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手鉴权拦截器
 * <p>
 * Spring Security 的 {@code @EnableWebSecurity} 只作用于 servlet HTTP 过滤器链，
 * 不会拦截 {@code WebSocketConfigurer} 注册的握手请求，因此 {@code /ws/agent}
 * 必须在握手阶段独立校验令牌。
 * <p>
 * 浏览器端 WebSocket API 无法设置自定义请求头，令牌通过 {@code token} 查询参数传入；
 * 握手成功后由 {@code AgentWebSocketHandler} 从会话属性中读取认证主体。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WebSocketTokenInterceptor implements HandshakeInterceptor {

    public static final String AUTH_PRINCIPAL = "easywing.ai.principal";

    private final JwtDecoder jwtDecoder;
    private final AiProperties properties;

    public WebSocketTokenInterceptor(JwtDecoder jwtDecoder, AiProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!properties.getWebsocket().isRequireToken()) {
            return true;
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket handshake rejected: missing token, uri={}", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            attributes.put(AUTH_PRINCIPAL, jwtDecoder.decode(token));
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid token, uri={}", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * 提取 Bearer 令牌：优先 Authorization 头，其次 {@code token} 查询参数。
     */
    private String extractToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearer.substring(7).trim();
        }

        String query = request.getURI().getQuery();
        if (!StringUtils.hasText(query)) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
