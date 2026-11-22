package com.easywing.platform.ai.prompt;

import java.util.List;

/**
 * Prompt 库持久化抽象：内存实现供本地开发，Redis 实现让多实例共享同一份 Prompt 库。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface PromptStore {

    void save(PromptDefinition prompt);

    void delete(String id);

    PromptDefinition findById(String id);

    List<PromptDefinition> listByUser(String userId, String tenantId);
}
