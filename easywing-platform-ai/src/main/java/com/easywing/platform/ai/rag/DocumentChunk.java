package com.easywing.platform.ai.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档块 - RAG 的基本单元
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /**
     * 文档块 ID
     */
    private String id;

    /**
     * 文档 ID
     */
    private String documentId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 向量嵌入
     */
    private float[] embedding;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 相似度分数
     */
    private double score;

    /**
     * 块索引（在原文档中的位置）
     */
    private int chunkIndex;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 来源
     */
    private String source;

    /**
     * 标签
     */
    private List<String> tags;
}
