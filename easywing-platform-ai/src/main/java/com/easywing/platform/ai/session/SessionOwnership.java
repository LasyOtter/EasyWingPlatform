package com.easywing.platform.ai.session;

/**
 * 会话归属校验：仅会话所有者（同租户）可读取或导出其会话数据。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class SessionOwnership {

    public static final String DEFAULT_TENANT = "default";

    private SessionOwnership() {}

    public static String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }

    public static boolean isOwned(ConversationSession session, String userId, String tenantId) {
        return session != null
                && userId != null
                && userId.equals(session.getUserId())
                && normalizeTenant(session.getTenantId()).equals(normalizeTenant(tenantId));
    }
}
