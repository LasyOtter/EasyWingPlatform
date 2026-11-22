package com.easywing.platform.ai.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息全文搜索命中项。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchHit {

    private String sessionId;

    private String sessionName;

    private String userId;

    private String tenantId;

    /** 命中消息下标；会话名命中为 -1 */
    private int messageIndex;

    /** 命中消息角色；会话名命中为 {@code session} */
    private String role;

    /** 命中片段（含省略号） */
    private String snippet;

    /** 会话最后活跃时间（epoch millis） */
    private long updatedAt;
}
