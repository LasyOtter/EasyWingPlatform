package com.easywing.platform.ai.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网检索请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchRequest {

    @NotBlank
    @Size(max = 500)
    private String query;

    @Builder.Default
    @Positive private int maxResults = 5;

    private String language;

    private String timeRange;

    @Builder.Default
    private boolean safeSearch = true;

    /** 是否抓取结果正文；关闭时仅返回搜索摘要 */
    @Builder.Default
    private boolean fetchContent = true;

    /** 是否基于检索结果生成答案（需已配置 AI Provider） */
    @Builder.Default
    private boolean generateAnswer = true;
}
