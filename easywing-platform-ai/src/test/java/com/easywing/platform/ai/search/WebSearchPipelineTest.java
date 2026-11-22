package com.easywing.platform.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.provider.AiProvider;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * {@link WebSearchPipeline} 编排测试：关闭降级、抓取→重排→答案组合。
 */
class WebSearchPipelineTest {

    private AiProperties properties;
    private SearchProvider provider;
    private ContentFetcher fetcher;
    private ResultReranker reranker;
    private AiProviderRegistry registry;
    private AiProvider aiProvider;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        provider = mock(SearchProvider.class);
        fetcher = mock(ContentFetcher.class);
        reranker = mock(ResultReranker.class);
        registry = mock(AiProviderRegistry.class);
        aiProvider = mock(AiProvider.class);
        when(provider.name()).thenReturn("serper");
        when(fetcher.name()).thenReturn("html");
        when(reranker.name()).thenReturn("identity");
        org.mockito.Mockito.lenient().when(fetcher.fetch(any())).thenReturn(Mono.empty());
        when(registry.getDefaultProvider()).thenReturn(Optional.of(aiProvider));
        when(aiProvider.getSupportedModels())
                .thenReturn(List.of(ModelInfo.builder().id("gpt-4o-mini").build()));
    }

    private SearchResult result(String id, String url) {
        return SearchResult.builder()
                .id(id)
                .title("标题 " + id)
                .url(url)
                .snippet("摘要 " + id)
                .build();
    }

    private WebSearchPipeline pipeline(boolean enabled) {
        return new WebSearchPipeline(properties, provider, fetcher, reranker, registry, enabled);
    }

    private void stubAnswer(String text) {
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(List.of(ChatCompletionResponse.Choice.builder()
                        .index(0)
                        .message(ChatMessage.builder()
                                .role("assistant")
                                .content(text)
                                .build())
                        .build()))
                .build();
        when(aiProvider.chat(any(ChatCompletionRequest.class))).thenReturn(Mono.just(response));
    }

    @Test
    @DisplayName("未启用时返回 404")
    void disabledReturns404() {
        WebSearchPipeline disabled = pipeline(false);

        assertThatThrownBy(() -> disabled.search(
                                WebSearchRequest.builder().query("x").build(), "u", "t")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("组合抓取、重排并生成答案")
    void composesFetchRerankAnswer() {
        List<SearchResult> raw =
                new ArrayList<>(List.of(result("a", "https://a.example"), result("b", "https://b.example")));
        when(provider.search(any(SearchQuery.class))).thenReturn(Mono.just(raw));
        when(fetcher.fetch("https://a.example")).thenReturn(Mono.just("正文 a"));
        when(fetcher.fetch("https://b.example")).thenReturn(Mono.just("正文 b"));
        List<SearchResult> reordered = List.of(result("b", "https://b.example"), result("a", "https://a.example"));
        when(reranker.rerank(any(), any())).thenReturn(Mono.just(reordered));
        stubAnswer("综合答案");

        WebSearchResponse response = pipeline(true)
                .search(WebSearchRequest.builder().query("问题").build(), "user-1", "tenant-1")
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getAnswer()).isEqualTo("综合答案");
        assertThat(response.getProviders()).containsExactly("serper", "html", "identity");
        assertThat(response.getResults()).extracting(SearchResult::getId).containsExactly("b", "a");
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(aiProvider).chat(captor.capture());
        ChatCompletionRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(request.getUserId()).isEqualTo("user-1");
        assertThat(request.getTenantId()).isEqualTo("tenant-1");
        assertThat(request.getMessages().get(0).getContent()).contains("https://a.example");
    }

    @Test
    @DisplayName("抓取失败时降级为仅摘要，流水线不中断")
    void degradesWhenFetchFails() {
        List<SearchResult> raw = List.of(result("a", "https://a.example"));
        when(provider.search(any(SearchQuery.class))).thenReturn(Mono.just(raw));
        when(fetcher.fetch("https://a.example")).thenReturn(Mono.empty());
        when(reranker.rerank(any(), any())).thenReturn(Mono.just(raw));
        stubAnswer("答案");

        WebSearchResponse response = pipeline(true)
                .search(WebSearchRequest.builder().query("问题").build(), null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getResults().get(0).getContent()).isNull();
        assertThat(response.getAnswer()).isEqualTo("答案");
    }

    @Test
    @DisplayName("关闭抓取时不调用抓取器")
    void skipsFetcherWhenDisabled() {
        List<SearchResult> raw = List.of(result("a", "https://a.example"));
        when(provider.search(any(SearchQuery.class))).thenReturn(Mono.just(raw));
        when(reranker.rerank(any(), any())).thenReturn(Mono.just(raw));
        stubAnswer("答案");

        WebSearchRequest request =
                WebSearchRequest.builder().query("问题").fetchContent(false).build();
        pipeline(true).search(request, null, null).block();

        verify(fetcher, never()).fetch(any());
    }

    @Test
    @DisplayName("未配置 AI Provider 时答案为空，不影响检索结果")
    void skipsAnswerWithoutProvider() {
        when(registry.getDefaultProvider()).thenReturn(Optional.empty());
        List<SearchResult> raw = List.of(result("a", "https://a.example"));
        when(provider.search(any(SearchQuery.class))).thenReturn(Mono.just(raw));
        when(reranker.rerank(any(), any())).thenReturn(Mono.just(raw));

        WebSearchResponse response = pipeline(true)
                .search(WebSearchRequest.builder().query("问题").build(), null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isEmpty();
        verify(aiProvider, never()).chat(any());
    }

    @Test
    @DisplayName("未配置搜索提供商时返回 400")
    void missingProviderReturns400() {
        WebSearchPipeline noProvider = new WebSearchPipeline(properties, null, fetcher, reranker, registry, true);

        assertThatThrownBy(() -> noProvider
                        .search(WebSearchRequest.builder().query("x").build(), null, null)
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
