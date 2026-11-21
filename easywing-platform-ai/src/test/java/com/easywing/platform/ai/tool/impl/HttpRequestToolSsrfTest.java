package com.easywing.platform.ai.tool.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link HttpRequestTool} SSRF 防护测试
 * <p>
 * 只覆盖纯地址判定与入参校验，不发起真实网络请求。
 */
class HttpRequestToolSsrfTest {

    private final HttpRequestTool tool = new HttpRequestTool(WebClient.builder());

    private boolean blocked(String host) throws Exception {
        return tool.isBlockedAddress(InetAddress.getByName(host));
    }

    @Test
    @DisplayName("IPv4 环回与私网地址被拦截")
    void blocksPrivateIPv4() throws Exception {
        for (String host : new String[] {
            "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0", "224.0.0.1"
        }) {
            assertTrue(blocked(host), host + " 应被拦截");
        }
    }

    @Test
    @DisplayName("十进制形式的环回地址被规范化后拦截")
    void blocksObfuscatedLoopback() throws Exception {
        // 2130706433 == 127.0.0.1
        assertTrue(blocked("2130706433"));
    }

    @Test
    @DisplayName("IPv6 环回、链路本地与唯一本地地址被拦截")
    void blocksPrivateIPv6() throws Exception {
        assertTrue(blocked("::1"));
        assertTrue(blocked("fe80::1"));
        assertTrue(blocked("fc00::1"));
        assertTrue(blocked("fd12:3456:789a::1"));
    }

    @Test
    @DisplayName("IPv4 映射的 IPv6 内网地址被拦截")
    void blocksIpv4MappedIpv6() throws Exception {
        assertTrue(blocked("::ffff:127.0.0.1"));
        assertTrue(blocked("::ffff:169.254.169.254"));
    }

    @Test
    @DisplayName("公网地址放行")
    void allowsPublicAddress() throws Exception {
        // 8.8.8.8 为 Google 公共 DNS
        assertFalse(blocked("8.8.8.8"));
    }

    @Test
    @DisplayName("内网主机名在解析阶段被拒绝")
    void rejectsInternalHost() {
        Map<String, Object> args = Map.of("url", "http://169.254.169.254/latest/meta-data");
        Map<String, Object> result = tool.execute(args).block();

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("目标地址不允许访问"), "实际错误: " + result.get("error"));
    }

    @Test
    @DisplayName("localhost 主机名在解析阶段被拒绝")
    void rejectsLocalhost() {
        Map<String, Object> args = Map.of("url", "http://localhost:8082/api/v1/ai/health");
        Map<String, Object> result = tool.execute(args).block();

        assertFalse((Boolean) result.get("success"));
    }

    @Test
    @DisplayName("非 http/https 协议被拒绝")
    void rejectsNonHttpScheme() {
        Map<String, Object> result =
                tool.execute(Map.of("url", "file:///etc/passwd")).block();
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    @DisplayName("混淆写法解析后仍为内网地址")
    void parsesObfuscatedUri() throws Exception {
        URI uri = URI.create("http://2130706433/");
        assertTrue(blocked(uri.getHost()));
    }
}
