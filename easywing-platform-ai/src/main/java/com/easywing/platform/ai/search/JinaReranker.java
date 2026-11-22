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
 * Jina Rerank 重排器（{@code POST {baseUrl}/rerank}）。
 * <p>
 * 需要 API Key，默认关闭；未配置时流水线使用 {@link IdentityReranker}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class JinaReranker implements ResultReranker {

    private static final String NAME = "jina";

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public JinaReranker(WebClient webClient, String baseUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<List<SearchResult>> rerank(String query, List<SearchResult> results) {
        if (results.isEmpty()) {
            return Mono.just(results);
        }
        List<String> documents = new ArrayList<>();
        for (SearchResult result : results) {
            StringBuilder doc = new StringBuilder();
            if (StringUtils.hasText(result.getTitle())) {
                doc.append(result.getTitle()).append('\n');
            }
            doc.append(StringUtils.hasText(result.getContent()) ? result.getContent() : result.getSnippet());
            documents.add(doc.toString());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", results.size());

        return webClient
                .post()
                .uri(baseUrl.replaceAll("/+$", "") + "/rerank")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseResponse(raw, results))
                .onErrorMap(
                        ResponseStatusException.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "重排服务请求失败: " + ex.getReason()));
    }

    /**
     * 按响应 {@code results[].index} 重排，并把 {@code relevance_score} 写回 {@code score}。
     */
    List<SearchResult> parseResponse(String raw, List<SearchResult> original) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.path("results");
            if (!items.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "重排响应格式错误");
            }
            List<SearchResult> ordered = new ArrayList<>();
            for (JsonNode item : items) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= original.size()) {
                    continue;
                }
                SearchResult source = original.get(index);
                ordered.add(SearchResult.builder()
                        .id(source.getId())
                        .title(source.getTitle())
                        .url(source.getUrl())
                        .snippet(source.getSnippet())
                        .content(source.getContent())
                        .score(item.path("relevance_score").asDouble(source.getScore()))
                        .provider(source.getProvider())
                        .publishedAt(source.getPublishedAt())
                        .build());
            }
            if (ordered.isEmpty()) {
                return original;
            }
            return ordered;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析重排响应失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "重排响应解析失败");
        }
    }
}
