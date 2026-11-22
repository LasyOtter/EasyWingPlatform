package com.easywing.platform.ai.search;

import reactor.core.publisher.Mono;

/**
 * 内容抓取器 - 按 URL 取回正文文本。
 * <p>
 * 流水线第二段，可独立替换（如 Readability、Jina Reader 等）。实现须自带 SSRF 防护与大小上限。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface ContentFetcher {

    /** 抓取器名称 */
    String name();

    /**
     * 抓取并清洗正文；抓取失败返回 {@link Mono#empty()}，由流水线降级为仅使用摘要。
     */
    Mono<String> fetch(String url);
}
