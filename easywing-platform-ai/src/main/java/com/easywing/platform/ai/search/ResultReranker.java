package com.easywing.platform.ai.search;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 结果重排器 - 按与查询的相关性重排检索结果。
 * <p>
 * 流水线第三段，可独立替换；默认使用恒等重排（保持提供商顺序），可配置接入 Jina Rerank。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface ResultReranker {

    /** 重排器名称 */
    String name();

    Mono<List<SearchResult>> rerank(String query, List<SearchResult> results);
}
