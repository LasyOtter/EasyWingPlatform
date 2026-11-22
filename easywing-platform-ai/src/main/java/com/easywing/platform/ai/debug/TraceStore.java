package com.easywing.platform.ai.debug;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行追踪存储
 * <p>
 * 内存实现供本地开发使用；Redis 实现让多个 AI 实例共享同一份追踪数据，
 * 管理员在任一实例上都能查询到全局 Agent 执行链路，而不是只看到本实例的局部追踪。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface TraceStore {

    /**
     * 保存（或覆盖）一条追踪快照。
     */
    void save(AgentTrace trace);

    /**
     * 按追踪 ID 读取。
     */
    Optional<AgentTrace> load(String traceId);

    /**
     * 读取全部追踪。
     */
    List<AgentTrace> loadAll();

    /**
     * 按追踪 ID 删除。
     */
    void delete(String traceId);

    /**
     * 清空全部追踪。
     */
    void clear();
}
