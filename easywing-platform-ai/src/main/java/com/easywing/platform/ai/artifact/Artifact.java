package com.easywing.platform.ai.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话消息中提取的可运行产物（HTML / React / Mermaid）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {

    public enum Type {
        HTML,
        REACT,
        MERMAID
    }

    private String id;
    private String sessionId;
    private String userId;
    private String tenantId;
    private Type type;
    private String title;
    private String language;
    private String code;
    private long createdAt;
}
