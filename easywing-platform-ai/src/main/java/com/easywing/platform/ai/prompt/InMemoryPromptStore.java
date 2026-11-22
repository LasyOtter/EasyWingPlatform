package com.easywing.platform.ai.prompt;

import com.easywing.platform.ai.session.SessionOwnership;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Prompt 库内存存储。
 * <p>
 * 当 {@code easywing.ai.prompt.storage-type} 不是 {@code redis} 时生效，仅本实例可见，重启即丢失。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.prompt",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryPromptStore implements PromptStore {

    private final Map<String, PromptDefinition> prompts = new ConcurrentHashMap<>();

    public InMemoryPromptStore() {
        log.warn("AI Prompt 库使用内存存储（easywing.ai.prompt.storage-type=memory）：重启即丢失，集群下不共享，禁止用于生产");
    }

    @Override
    public void save(PromptDefinition prompt) {
        if (prompt == null || prompt.getId() == null) {
            return;
        }
        prompts.put(prompt.getId(), prompt);
    }

    @Override
    public void delete(String id) {
        prompts.remove(id);
    }

    @Override
    public PromptDefinition findById(String id) {
        return prompts.get(id);
    }

    @Override
    public List<PromptDefinition> listByUser(String userId, String tenantId) {
        String normalizedTenant = SessionOwnership.normalizeTenant(tenantId);
        List<PromptDefinition> result = new ArrayList<>();
        for (PromptDefinition prompt : prompts.values()) {
            if (prompt.getUserId() != null
                    && prompt.getUserId().equals(userId)
                    && SessionOwnership.normalizeTenant(prompt.getTenantId()).equals(normalizedTenant)) {
                result.add(prompt);
            }
        }
        result.sort(Comparator.comparingLong(PromptDefinition::getUpdatedAt).reversed());
        return result;
    }
}
