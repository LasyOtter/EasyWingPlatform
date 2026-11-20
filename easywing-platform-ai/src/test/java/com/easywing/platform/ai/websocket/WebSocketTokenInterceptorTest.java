package com.easywing.platform.ai.websocket;

import com.easywing.platform.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WebSocketTokenInterceptor} 单元测试
 * <p>
 * WebSocket 握手不经 Spring Security 过滤器链，该拦截器是 {@code /ws/agent}
 * 的唯一鉴权屏障，需独立验证。握手走 servlet 侧，故用 {@link MockHttpServletRequest}。
 */
class WebSocketTokenInterceptorTest {

    private JwtDecoder jwtDecoder;
    private AiProperties properties;
    private WebSocketTokenInterceptor interceptor;
    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        properties = new AiProperties();
        interceptor = new WebSocketTokenInterceptor(jwtDecoder, properties);
        handler = mock(WebSocketHandler.class);
    }

    private record Handshake(ServerHttpRequest request, MockHttpServletResponse raw,
                            ServerHttpResponse response, Map<String, Object> attrs) {
    }

    private Handshake handshake(String uri, String authHeader) {
        int q = uri.indexOf('?');
        String path = q >= 0 ? uri.substring(0, q) : uri;
        String query = q >= 0 ? uri.substring(q + 1) : null;

        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", path);
        servlet.setQueryString(query);
        if (authHeader != null) {
            servlet.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
        MockHttpServletResponse raw = new MockHttpServletResponse();
        return new Handshake(new ServletServerHttpRequest(servlet), raw,
                new ServletServerHttpResponse(raw), new HashMap<>());
    }

    private boolean proceed(Handshake h) {
        return interceptor.beforeHandshake(h.request(), h.response(), handler, h.attrs());
    }

    @Test
    @DisplayName("缺少令牌时拒绝握手并返回 401")
    void rejectsMissingToken() {
        properties.getWebsocket().setRequireToken(true);
        Handshake h = handshake("/ws/agent", null);

        assertFalse(proceed(h));
        assertEquals(401, h.raw().getStatus());
    }

    @Test
    @DisplayName("无效令牌时拒绝握手")
    void rejectsInvalidToken() {
        properties.getWebsocket().setRequireToken(true);
        when(jwtDecoder.decode(anyString())).thenThrow(
                new JwtValidationException("bad token",
                        List.of(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
        Handshake h = handshake("/ws/agent?token=abc", null);

        assertFalse(proceed(h));
        assertTrue(h.attrs().isEmpty());
    }

    @Test
    @DisplayName("有效令牌时放行并写入认证主体")
    void acceptsValidToken() {
        properties.getWebsocket().setRequireToken(true);
        when(jwtDecoder.decode("abc"))
                .thenReturn(Jwt.withTokenValue("abc").header("alg", "none").subject("user-1").build());
        Handshake h = handshake("/ws/agent?token=abc", null);

        assertTrue(proceed(h));
        assertNotNull(h.attrs().get(WebSocketTokenInterceptor.AUTH_PRINCIPAL));
    }

    @Test
    @DisplayName("Authorization: Bearer 头优先于查询参数")
    void prefersAuthorizationHeader() {
        properties.getWebsocket().setRequireToken(true);
        when(jwtDecoder.decode("header-token"))
                .thenReturn(Jwt.withTokenValue("header-token").header("alg", "none").subject("u").build());
        Handshake h = handshake("/ws/agent?token=query-token", "Bearer header-token");

        assertTrue(proceed(h));
        verify(jwtDecoder, never()).decode("query-token");
    }

    @Test
    @DisplayName("requireToken=false 时不校验也不解析令牌")
    void skipsCheckWhenDisabled() {
        properties.getWebsocket().setRequireToken(false);
        Handshake h = handshake("/ws/agent", null);

        assertTrue(proceed(h));
        verify(jwtDecoder, never()).decode(anyString());
        assertNull(h.attrs().get(WebSocketTokenInterceptor.AUTH_PRINCIPAL));
    }

    @Test
    @DisplayName("令牌缺失时解码器不被调用")
    void doesNotDecodeWhenTokenAbsent() {
        properties.getWebsocket().setRequireToken(true);
        proceed(handshake("/ws/agent", null));

        verify(jwtDecoder, never()).decode(anyString());
    }
}
