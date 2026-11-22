package com.easywing.platform.ai.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话共享链接（PRD 4.7「共享链接限流」、5.2「共享链接须限流」）。
 * <p>
 * 链接以不可猜测的随机 token 作为 ID，只有令牌持有者可读取被共享会话的只读视图；
 * 过期、撤销、访问次数耗尽一律对外表现为不可见，不泄露资源存在性。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLink {

    /** 共享令牌（对外暴露的唯一标识）。 */
    private String id;

    /** 被共享的会话 ID。 */
    private String sessionId;

    /** 创建者用户 ID。 */
    private String userId;

    /** 创建者租户。 */
    private String tenantId;

    /** 创建时间（epoch millis）。 */
    private long createdAt;

    /** 过期时间（epoch millis）；0 表示永不过期。 */
    private long expiresAt;

    /** 是否已撤销。 */
    private boolean revoked;

    /** 最大访问次数；0 表示不限。 */
    private int maxAccesses;

    /** 已访问次数。 */
    private int accessCount;

    /** 最近一次访问时间（epoch millis）。 */
    private long lastAccessAt;

    /** 链接是否已失效（过期或撤销或次数耗尽）。 */
    public boolean isUsable(long now) {
        if (revoked) {
            return false;
        }
        if (expiresAt > 0 && now >= expiresAt) {
            return false;
        }
        return maxAccesses <= 0 || accessCount < maxAccesses;
    }
}
