package com.easywing.platform.ai.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 共享链接的进程内存存储（默认，重启丢失）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.share",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryShareLinkStore implements ShareLinkStore {

    private final ConcurrentHashMap<String, ShareLink> links = new ConcurrentHashMap<>();

    @Override
    public void save(ShareLink link) {
        if (link != null && link.getId() != null) {
            links.put(link.getId(), link);
        }
    }

    @Override
    public ShareLink findById(String id) {
        return id == null ? null : links.get(id);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            links.remove(id);
        }
    }

    @Override
    public List<ShareLink> listBySession(String sessionId) {
        List<ShareLink> result = new ArrayList<>();
        if (sessionId == null) {
            return result;
        }
        for (ShareLink link : links.values()) {
            if (sessionId.equals(link.getSessionId())) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparingLong(ShareLink::getCreatedAt).reversed());
        return result;
    }

    @Override
    public List<ShareLink> listByUser(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<ShareLink> result = new ArrayList<>();
        for (ShareLink link : links.values()) {
            if (userId != null
                    && userId.equals(link.getUserId())
                    && SessionOwnership.normalizeTenant(link.getTenantId()).equals(normalizedTenant)) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparingLong(ShareLink::getCreatedAt).reversed());
        return result;
    }
}
