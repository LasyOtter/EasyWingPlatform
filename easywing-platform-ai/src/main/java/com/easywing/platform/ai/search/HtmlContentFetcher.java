package com.easywing.platform.ai.search;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 基于 WebClient 的 HTML 正文抓取器。
 * <p>
 * 仅允许 http/https，并按 IP 判定拒绝内网、环回、链路与组播地址（含 IPv4 映射 IPv6），
 * 防止 SSRF；不跟随重定向。正文经标签清洗后截断到 {@code maxChars}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class HtmlContentFetcher implements ContentFetcher {

    private static final String NAME = "html";
    private static final Set<String> BLOCKED_HOST_NAMES =
            Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]", "metadata.google.internal");

    private final WebClient webClient;
    private final int maxBytes;
    private final int maxChars;

    public HtmlContentFetcher(WebClient webClient, int maxBytes, int maxChars) {
        this.webClient = webClient;
        this.maxBytes = maxBytes;
        this.maxChars = maxChars;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<String> fetch(String url) {
        if (!StringUtils.hasText(url)) {
            return Mono.empty();
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Mono.error(badRequest("非法的 URL"));
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Mono.error(badRequest("仅支持 http(s) 链接"));
        }
        if (!isPublicHost(uri.getHost())) {
            return Mono.error(badRequest("目标地址被拒绝（内网或保留地址）"));
        }

        return webClient
                .get()
                .uri(uri)
                .accept(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML, MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    if (bytes.length > maxBytes) {
                        throw badRequest("页面内容超过抓取上限");
                    }
                    String text = htmlToText(new String(bytes, StandardCharsets.UTF_8));
                    return text.length() > maxChars ? text.substring(0, maxChars) : text;
                })
                .onErrorMap(
                        ResponseStatusException.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "抓取页面失败: " + ex.getReason()));
    }

    /**
     * 校验目标主机是否为可访问的公网地址。仅按 IP 判定，避免数字写法绕过。
     */
    boolean isPublicHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
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
     */
    boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] raw = ipv6.getAddress();
            if ((raw[0] & 0xFE) == 0xFC) {
                return true;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                byte[] v4 = new byte[] {raw[12], raw[13], raw[14], raw[15]};
                try {
                    return isBlockedAddress(InetAddress.getByAddress(v4));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 把 HTML 粗略清洗为纯文本：去脚本样式、还原换行、去标签、解码常见实体、压缩空白。
     */
    static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("(?is)<(script|style|noscript|template)[^>]*>.*?</\\1>", " ");
        text = text.replaceAll("(?is)<br\\s*/?>", "\n");
        text = text.replaceAll("(?is)</(p|div|li|h[1-6]|tr|pre)>", "\n");
        text = text.replaceAll("(?s)<[^>]+>", " ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("(\\n\\s*){3,}", "\n\n");
        return text.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
