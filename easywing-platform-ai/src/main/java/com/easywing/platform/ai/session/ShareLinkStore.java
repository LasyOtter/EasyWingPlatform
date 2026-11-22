package com.easywing.platform.ai.session;

import java.util.List;

/**
 * 共享链接存储。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface ShareLinkStore {

    void save(ShareLink link);

    ShareLink findById(String id);

    void delete(String id);

    /** 列出某个会话下的全部共享链接。 */
    List<ShareLink> listBySession(String sessionId);

    /** 列出某用户（同租户）的全部共享链接。 */
    List<ShareLink> listByUser(String userId, String tenantId);
}
