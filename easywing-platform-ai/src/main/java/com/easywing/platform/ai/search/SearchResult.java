package com.easywing.platform.ai.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条联网检索结果。
 * <p>
 * {@code snippet} 来自搜索提供商，{@code content} 为抓取器取回并清洗后的正文，
 * {@code score} 为原始排名或重排后的相关性分数。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String id;
    private String title;
    private String url;
    private String snippet;
    private String content;
    private double score;
    private String provider;
    private long publishedAt;
}
