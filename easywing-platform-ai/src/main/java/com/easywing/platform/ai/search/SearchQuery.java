package com.easywing.platform.ai.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网检索查询条件。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchQuery {

    @NotBlank
    @Size(max = 500)
    private String query;

    /** 期望返回的搜索结果条数 */
    @Builder.Default
    private int maxResults = 5;

    /** 结果语言，如 zh-cn / en-US */
    private String language;

    /** 时间范围，如 day / week / month / year */
    private String timeRange;

    /** 是否启用安全搜索 */
    @Builder.Default
    private boolean safeSearch = true;
}
