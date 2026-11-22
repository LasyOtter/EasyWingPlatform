package com.easywing.platform.ai.search;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网检索结果。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchResponse {

    private String query;
    private List<SearchResult> results;
    private int count;
    /** 本次使用的组件名：provider / fetcher / reranker */
    private List<String> providers;

    private long elapsedMs;
    /** 基于检索结果生成的答案，未生成时为空串 */
    private String answer;
}
