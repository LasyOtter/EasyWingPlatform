package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HTTP 请求工具 - 发送 HTTP 请求
 */
@Slf4j
@Component
public class HttpRequestTool implements AiTool {

    private final WebClient webClient;
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024; // 1MB
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final Set<String> BLOCKED_HOST_NAMES = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]", "metadata.google.internal");

    public HttpRequestTool(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, "EasyWing-AI-Agent/1.0")
                .build();
    }

    @Override
    public String getName() {
        return "http_request";
    }

    @Override
    public String getDescription() {
        return "发送 HTTP 请求获取数据。" +
                "输入参数：url (string) - 请求 URL。" +
                "method (string, 可选) - HTTP 方法，默认 GET，支持: GET, POST, PUT, DELETE。" +
                "headers (object, 可选) - 请求头键值对。" +
                "body (object, 可选) - 请求体，用于 POST/PUT";
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        String method = String.valueOf(arguments.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) arguments.get("headers");
        Object body = arguments.get("body");

        if (url == null || url.trim().isEmpty()) {
            return Mono.just(createErrorResponse("url 参数不能为空"));
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Mono.just(createErrorResponse("url 格式非法"));
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Mono.just(createErrorResponse("只支持 http 和 https 协议"));
        }
        if (uri.getHost() == null) {
            return Mono.just(createErrorResponse("url 缺少主机名"));
        }
        if (!ALLOWED_METHODS.contains(method)) {
            return Mono.just(createErrorResponse("不支持的 HTTP 方法: " + method));
        }

        log.info("Executing HTTP request: {} {}", method, url);

        // DNS 解析与内网地址校验放在弹性线程池，避免阻塞响应式线程
        return Mono.fromCallable(() -> isPublicHost(uri.getHost()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(publicHost -> {
                    if (!publicHost) {
                        return Mono.just(createErrorResponse("目标地址不允许访问"));
                    }
                    return executeRequest(method, uri, headers, body, url);
                });
    }

    /**
     * 执行 HTTP 请求并组装结果
     */
    private Mono<Map<String, Object>> executeRequest(
            String method, URI uri, Map<String, String> headers, Object body, String url) {

        WebClient.RequestBodySpec requestSpec = webClient.method(HttpMethod.valueOf(method)).uri(uri);

        if (headers != null) {
            headers.forEach(requestSpec::header);
        }

        if (body != null && ("POST".equals(method) || "PUT".equals(method))) {
            requestSpec.contentType(MediaType.APPLICATION_JSON);
            requestSpec.bodyValue(body);
        }

        // 不跟随重定向：目标地址的二次解析绕过了 isPublicHost 校验，会造成 SSRF。
        // 3xx 原样返回给调用方，由调用方决定是否再次请求。
        return requestSpec.exchangeToMono(response -> buildResponse(response, method, url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .onErrorResume(e -> {
                    log.error("HTTP request failed: {} {}", method, url, e);
                    return Mono.just(createErrorResponse("请求执行失败"));
                });
    }

    /**
     * 组装响应，真实反映 HTTP 状态码
     */
    private Mono<Map<String, Object>> buildResponse(ClientResponse response, String method, String url) {
        int statusCode = response.statusCode().value();
        return readBody(response, MAX_RESPONSE_SIZE)
                .map(content -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", statusCode >= 200 && statusCode < 300);
                    result.put("url", url);
                    result.put("method", method);
                    result.put("statusCode", statusCode);
                    result.put("body", content.content());
                    result.put("bodyLength", content.length());
                    result.put("truncated", content.truncated());
                    return result;
                });
    }

    /**
     * 读取响应体并限制大小，超出部分直接截断，避免大响应占满内存
     */
    private Mono<BodyContent> readBody(ClientResponse response, int maxSize) {
        return response.bodyToFlux(DataBuffer.class)
                .collect(() -> new BodyContent(maxSize), BodyContent::append);
    }

    /**
     * 校验目标主机是否为可访问的公网地址，防止 SSRF。
     * <p>
     * 仅按 IP 判定，不依赖主机名字符串黑名单：{@code 2130706433}、{@code 0x7f000001}
     * 这类写法会被 {@link InetAddress} 规范化后命中内网判定。
     */
    private boolean isPublicHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOST_NAMES.contains(normalized)) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判定单个地址是否属于不可访问范围。
     * <p>
     * 除常规内网/环回/链路本地外，额外覆盖 IPv4 映射的 IPv6 地址与 IPv6 唯一本地地址
     * （{@code fc00::/7}），避免通过 IPv6 形式绕过 IPv4 判定。
     */
    boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof java.net.Inet6Address ipv6) {
            byte[] raw = ipv6.getAddress();
            // fc00::/7 唯一本地地址
            if ((raw[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        // IPv4-mapped IPv6（::ffff:127.0.0.1）需按内层 IPv4 复判
        if (address instanceof java.net.Inet6Address) {
            byte[] raw = address.getAddress();
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                byte[] v4 = new byte[]{raw[12], raw[13], raw[14], raw[15]};
                try {
                    return isBlockedAddress(InetAddress.getByAddress(v4));
                } catch (UnknownHostException e) {
                    // getByAddress 只对长度 != 4 抛异常，此处长度固定为 4
                    return true;
                }
            }
        }
        return false;
    }

    private static final class BodyContent {
        private final int maxSize;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        BodyContent(int maxSize) {
            this.maxSize = maxSize;
        }

        BodyContent append(DataBuffer dataBuffer) {
            int readable = dataBuffer.readableByteCount();
            if (!truncated && buffer.size() + readable > maxSize) {
                int allowed = Math.max(0, maxSize - buffer.size());
                byte[] chunk = new byte[allowed];
                dataBuffer.read(chunk, 0, allowed);
                buffer.write(chunk, 0, allowed);
                truncated = true;
            } else if (!truncated) {
                byte[] chunk = new byte[readable];
                dataBuffer.read(chunk, 0, readable);
                buffer.write(chunk, 0, readable);
            }
            DataBufferUtils.release(dataBuffer);
            return this;
        }

        String content() {
            return buffer.toString(StandardCharsets.UTF_8);
        }

        int length() {
            return buffer.size();
        }

        boolean truncated() {
            return truncated;
        }
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }
}
