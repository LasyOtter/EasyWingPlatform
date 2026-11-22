package com.easywing.platform.ai.search;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 恒等重排 - 保持提供商返回顺序，作为默认实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class IdentityReranker implements ResultReranker {

    @Override
    public String name() {
        return "identity";
    }

    @Override
    public Mono<List<SearchResult>> rerank(String query, List<SearchResult> results) {
        return Mono.just(results);
    }
}
