package com.easywing.platform.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Serper（Google Search API）搜索提供商。
 * <p>
 * 请求 {@code POST {baseUrl}/search}，响应取 {@code organic[]}。密钥只经配置或环境变量注入。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class SerperSearchProvider implements SearchProvider {

    private static final String NAME = "serper";

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public SerperSearchProvider(WebClient webClient, String baseUrl, String apiKey, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<List<SearchResult>> search(SearchQuery query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query.getQuery());
        body.put("num", Math.max(1, query.getMaxResults()));
        if (StringUtils.hasText(query.getLanguage())) {
            body.put("hl", query.getLanguage());
        }
        if (StringUtils.hasText(query.getTimeRange())) {
            body.put("tbs", "qdr:" + query.getTimeRange().toLowerCase());
        }
        body.put("safe", query.isSafeSearch() ? "active" : "off");

        return webClient
                .post()
                .uri(baseUrl.replaceAll("/+$", "") + "/search")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseResponse(raw, query))
                .onErrorMap(
                        ResponseStatusException.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "搜索提供商请求失败: " + ex.getReason()));
    }

    /**
     * 解析响应体，独立为包内方法便于测试。
     */
    List<SearchResult> parseResponse(String raw, SearchQuery query) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode organic = root.path("organic");
            List<SearchResult> results = new ArrayList<>();
            if (organic.isArray()) {
                for (JsonNode node : organic) {
                    String url = text(node, "link");
                    if (!StringUtils.hasText(url)) {
                        continue;
                    }
                    results.add(SearchResult.builder()
                            .id(NAME + "-" + results.size())
                            .title(text(node, "title"))
                            .url(url)
                            .snippet(text(node, "snippet"))
                            .score(node.path("position").asDouble(results.size() + 1))
                            .provider(NAME)
                            .build());
                    if (results.size() >= query.getMaxResults()) {
                        break;
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("解析搜索响应失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "搜索响应解析失败");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
