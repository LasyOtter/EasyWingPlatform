package com.easywing.platform.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link SerperSearchProvider} 解析测试（不触网）。
 */
class SerperSearchProviderTest {

    private SerperSearchProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SerperSearchProvider(null, "https://google.serper.dev", "test-key", new ObjectMapper());
    }

    @Test
    @DisplayName("解析 organic 结果并标注提供商")
    void parsesOrganicResults() {
        String json =
                """
                {"organic":[
                  {"title":"EasyWing 文档","link":"https://easywing.io/docs","snippet":"平台文档","position":1},
                  {"title":"发布公告","link":"https://easywing.io/news","snippet":"最新公告","position":2}
                ]}
                """;
        SearchQuery query =
                SearchQuery.builder().query("EasyWing").maxResults(5).build();

        var results = provider.parseResponse(json, query);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("EasyWing 文档");
        assertThat(results.get(0).getUrl()).isEqualTo("https://easywing.io/docs");
        assertThat(results.get(0).getProvider()).isEqualTo("serper");
        assertThat(results.get(0).getId()).startsWith("serper-");
    }

    @Test
    @DisplayName("按 maxResults 截断结果")
    void respectsMaxResults() {
        String json =
                """
                {"organic":[
                  {"title":"a","link":"https://a.example","snippet":"s","position":1},
                  {"title":"b","link":"https://b.example","snippet":"s","position":2},
                  {"title":"c","link":"https://c.example","snippet":"s","position":3}
                ]}
                """;
        SearchQuery query = SearchQuery.builder().query("x").maxResults(2).build();

        assertThat(provider.parseResponse(json, query)).hasSize(2);
    }

    @Test
    @DisplayName("跳过缺少链接的条目")
    void skipsEntriesWithoutLink() {
        String json =
                "{\"organic\":[{\"title\":\"无链接\",\"snippet\":\"s\"},{\"title\":\"有链接\",\"link\":\"https://ok.example\",\"snippet\":\"s\"}]}";

        var results = provider.parseResponse(
                json, SearchQuery.builder().query("x").maxResults(5).build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUrl()).isEqualTo("https://ok.example");
    }

    @Test
    @DisplayName("非法 JSON 返回 502")
    void rejectsInvalidJson() {
        SearchQuery query = SearchQuery.builder().query("x").maxResults(5).build();

        assertThatThrownBy(() -> provider.parseResponse("not-json", query)).isInstanceOf(ResponseStatusException.class);
    }
}
