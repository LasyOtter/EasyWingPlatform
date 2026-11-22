package com.easywing.platform.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JinaReranker} 解析测试（不触网）。
 */
class JinaRerankerTest {

    private JinaReranker reranker;
    private List<SearchResult> original;

    @BeforeEach
    void setUp() {
        reranker = new JinaReranker(null, "https://api.jina.ai/v1", "test-key", "jina-reranker", new ObjectMapper());
        original = List.of(
                result("a", "https://a.example", "摘要 a"),
                result("b", "https://b.example", "摘要 b"),
                result("c", "https://c.example", "摘要 c"));
    }

    private SearchResult result(String id, String url, String snippet) {
        return SearchResult.builder()
                .id(id)
                .title("标题 " + id)
                .url(url)
                .snippet(snippet)
                .build();
    }

    @Test
    @DisplayName("按响应 index 重排并写回相关性分数")
    void reordersByIndex() {
        String json =
                """
                {"results":[
                  {"index":2,"relevance_score":0.91},
                  {"index":0,"relevance_score":0.53}
                ]}
                """;

        List<SearchResult> ordered = reranker.parseResponse(json, original);

        assertThat(ordered).extracting(SearchResult::getId).containsExactly("c", "a");
        assertThat(ordered.get(0).getScore()).isEqualTo(0.91);
        assertThat(ordered.get(1).getScore()).isEqualTo(0.53);
    }

    @Test
    @DisplayName("保留原有标题与链接")
    void preservesFields() {
        String json = "{\"results\":[{\"index\":1,\"relevance_score\":0.8}]}";

        SearchResult first = reranker.parseResponse(json, original).get(0);

        assertThat(first.getId()).isEqualTo("b");
        assertThat(first.getTitle()).isEqualTo("标题 b");
        assertThat(first.getUrl()).isEqualTo("https://b.example");
    }

    @Test
    @DisplayName("空重排结果回退原列表")
    void fallsBackWhenEmpty() {
        List<SearchResult> ordered = reranker.parseResponse("{\"results\":[]}", original);

        assertThat(ordered).extracting(SearchResult::getId).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("空输入直接返回，不调用重排服务")
    void emptyInputShortCircuits() {
        assertThat(reranker.rerank("q", List.of()).block()).isEmpty();
    }
}
