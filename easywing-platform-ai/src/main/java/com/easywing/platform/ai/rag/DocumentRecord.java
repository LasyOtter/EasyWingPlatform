package com.easywing.platform.ai.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已入库文档的目录记录，包含归属信息与分块统计。
 * <p>
 * 向量块本身由 {@link VectorStore} 保存，本记录用于按用户/租户列出、查看与删除文档。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord {

    private String id;
    private String userId;
    private String tenantId;
    private String title;
    private String fileName;
    private String source;
    private String contentType;
    private long sizeBytes;
    private int chunkCount;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private long createdAt;
}
