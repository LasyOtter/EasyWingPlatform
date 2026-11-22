package com.easywing.platform.ai.search;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ModelInfo;
import com.easywing.platform.ai.provider.AiProvider;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 联网检索流水线：搜索提供商 → 内容抓取器 → 结果重排器，三段均可独立替换。
 * <p>
 * 默认关闭（{@code easywing.ai.web-search.enabled=false}）；密钥只经配置或环境变量注入。
 * 若已配置 AI Provider 且请求要求生成答案，会在重排结果之上再调用一次模型做归纳。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class WebSearchPipeline {

    private static final int ANSWER_CONTEXT_LIMIT = 6000;

    private final AiProperties properties;
    private final AiProviderRegistry registry;
    private final SearchProvider provider;
    private final ContentFetcher fetcher;
    private final ResultReranker reranker;
    private final boolean enabled;

    @Autowired
    public WebSearchPipeline(
            AiProperties properties, WebClient webClient, ObjectMapper objectMapper, AiProviderRegistry registry) {
        this(
                properties,
                buildProvider(properties, webClient, objectMapper),
                buildFetcher(properties, webClient),
                buildReranker(properties, webClient, objectMapper),
                registry,
                properties.getWebSearch().isEnabled());
    }

    /**
     * 供测试注入组件。
     */
    WebSearchPipeline(
            AiProperties properties,
            SearchProvider provider,
            ContentFetcher fetcher,
            ResultReranker reranker,
            AiProviderRegistry registry,
            boolean enabled) {
        this.properties = properties;
        this.provider = provider;
        this.fetcher = fetcher;
        this.reranker = reranker;
        this.registry = registry;
        this.enabled = enabled;
    }

    public Mono<WebSearchResponse> search(WebSearchRequest request, String userId, String tenantId) {
        if (!enabled) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "联网检索未启用"));
        }
        if (provider == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置可用的搜索提供商"));
        }
        int maxResults = Math.max(1, request.getMaxResults());
        SearchQuery query = SearchQuery.builder()
                .query(request.getQuery().trim())
                .maxResults(maxResults)
                .language(request.getLanguage())
                .timeRange(request.getTimeRange())
                .safeSearch(request.isSafeSearch())
                .build();

        long startedAt = System.currentTimeMillis();
        return provider.search(query)
                .flatMapMany(results -> fetchContents(request, results))
                .collectList()
                .flatMap(results -> reranker.rerank(query.getQuery(), results))
                .flatMap(results -> {
                    List<SearchResult> ordered = new ArrayList<>(results);
                    return generateAnswer(request, query.getQuery(), ordered, userId, tenantId)
                            .map(answer -> buildResponse(query.getQuery(), ordered, answer, startedAt));
                });
    }

    /**
     * 并发抓取结果正文；未要求抓取或抓取失败时降级为仅摘要。
     */
    private Flux<SearchResult> fetchContents(WebSearchRequest request, List<SearchResult> results) {
        if (!request.isFetchContent() || fetcher == null) {
            return Flux.fromIterable(results);
        }
        int limit = Math.max(0, properties.getWebSearch().getFetchLimit());
        List<SearchResult> targets =
                limit == 0 ? List.of() : results.stream().limit(limit).toList();
        Flux<SearchResult> head = Flux.fromIterable(targets).concatMap(result -> fetcher.fetch(result.getUrl())
                .map(text -> withContent(result, text))
                .defaultIfEmpty(result));
        if (targets.size() >= results.size()) {
            return head;
        }
        return head.concatWith(Flux.fromIterable(results.subList(targets.size(), results.size())));
    }

    private SearchResult withContent(SearchResult source, String content) {
        return SearchResult.builder()
                .id(source.getId())
                .title(source.getTitle())
                .url(source.getUrl())
                .snippet(source.getSnippet())
                .content(content)
                .score(source.getScore())
                .provider(source.getProvider())
                .publishedAt(source.getPublishedAt())
                .build();
    }

    private Mono<String> generateAnswer(
            WebSearchRequest request, String query, List<SearchResult> results, String userId, String tenantId) {
        if (!request.isGenerateAnswer() || registry == null) {
            return Mono.just("");
        }
        Optional<AiProvider> aiProvider = registry.getDefaultProvider();
        if (aiProvider.isEmpty()) {
            log.debug("未配置 AI Provider，跳过联网检索答案生成");
            return Mono.just("");
        }
        String context = buildContext(results);
        if (!StringUtils.hasText(context)) {
            return Mono.just("");
        }
        String system = "你是一个联网检索助手。仅依据以下检索到的资料回答用户问题，" + "资料不足时明确说明；用中文作答，并在引用处标注来源链接。";
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(resolveModel(aiProvider.get()))
                .systemPrompt(system)
                .messages(List.of(ChatMessage.builder()
                        .role("user")
                        .content("检索资料：\n" + context + "\n\n问题：" + query)
                        .build()))
                .temperature(0.2)
                .maxTokens(2048)
                .userId(userId)
                .tenantId(tenantId)
                .build();
        return aiProvider.get().chat(chatRequest).map(response -> extractText(response));
    }

    private String buildContext(List<SearchResult> results) {
        StringBuilder builder = new StringBuilder();
        for (SearchResult result : results) {
            String body = StringUtils.hasText(result.getContent()) ? result.getContent() : result.getSnippet();
            if (!StringUtils.hasText(body)) {
                continue;
            }
            builder.append("标题：")
                    .append(StringUtils.hasText(result.getTitle()) ? result.getTitle() : "")
                    .append('\n')
                    .append("来源：")
                    .append(result.getUrl())
                    .append('\n')
                    .append(body)
                    .append("\n\n");
            if (builder.length() > ANSWER_CONTEXT_LIMIT) {
                return builder.substring(0, ANSWER_CONTEXT_LIMIT);
            }
        }
        return builder.toString().trim();
    }

    private static String extractText(ChatCompletionResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()) {
            return "";
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        return choice.getMessage() != null
                        && StringUtils.hasText(choice.getMessage().getContent())
                ? choice.getMessage().getContent()
                : "";
    }

    /**
     * 解析默认 Provider 的首选可用模型；无可用模型时回退到通用模型名。
     */
    private static String resolveModel(AiProvider aiProvider) {
        return aiProvider.getSupportedModels().stream()
                .map(ModelInfo::getId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("gpt-4o-mini");
    }

    private WebSearchResponse buildResponse(String query, List<SearchResult> results, String answer, long startedAt) {
        return WebSearchResponse.builder()
                .query(query)
                .results(results)
                .count(results.size())
                .providers(List.of(
                        provider != null ? provider.name() : "none",
                        fetcher != null ? fetcher.name() : "none",
                        reranker != null ? reranker.name() : "none"))
                .elapsedMs(System.currentTimeMillis() - startedAt)
                .answer(answer)
                .build();
    }

    private static SearchProvider buildProvider(
            AiProperties properties, WebClient webClient, ObjectMapper objectMapper) {
        AiProperties.WebSearchConfig config = properties.getWebSearch();
        if ("serper".equalsIgnoreCase(config.getProvider()) && StringUtils.hasText(config.getSerperApiKey())) {
            return new SerperSearchProvider(
                    webClient, config.getSerperBaseUrl(), config.getSerperApiKey(), objectMapper);
        }
        return null;
    }

    private static ContentFetcher buildFetcher(AiProperties properties, WebClient webClient) {
        AiProperties.WebSearchConfig config = properties.getWebSearch();
        return new HtmlContentFetcher(webClient, config.getMaxFetchBytes(), config.getMaxContentChars());
    }

    private static ResultReranker buildReranker(
            AiProperties properties, WebClient webClient, ObjectMapper objectMapper) {
        AiProperties.WebSearchConfig config = properties.getWebSearch();
        AiProperties.WebSearchConfig.JinaConfig jina = config.getJina();
        if (jina.isEnabled() && StringUtils.hasText(jina.getApiKey())) {
            return new JinaReranker(webClient, jina.getBaseUrl(), jina.getApiKey(), jina.getModel(), objectMapper);
        }
        return new IdentityReranker();
    }
}
