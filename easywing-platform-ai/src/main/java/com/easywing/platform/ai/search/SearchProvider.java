package com.easywing.platform.ai.search;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 搜索提供商 - 把查询交给具体检索服务并返回结果。
 * <p>
 * 流水线第一段，可独立替换（如 Serper、Tavily、Bing 等）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface SearchProvider {

    /** 提供商名称，用于配置选择与结果标注 */
    String name();

    Mono<List<SearchResult>> search(SearchQuery query);
}
