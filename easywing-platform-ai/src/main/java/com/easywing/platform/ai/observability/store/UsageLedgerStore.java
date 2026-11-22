package com.easywing.platform.ai.observability.store;

import com.easywing.platform.ai.observability.UsageRecord;
import java.util.List;

/**
 * 用量账本存储
 * <p>
 * 内存实现供本地开发使用；Redis 实现让多个 AI 实例共享同一份成本账本，
 * 避免管理员看到的用量与成本只是单个实例的局部数据。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface UsageLedgerStore {

    /**
     * 保存（或覆盖）一条用量记录，并按容量上限淘汰最旧记录。
     */
    void save(UsageRecord record);

    /**
     * 读取全部用量记录。
     */
    List<UsageRecord> loadAll();

    /**
     * 按记录 ID 删除。
     */
    void delete(String id);

    /**
     * 清空账本。
     */
    void clear();
}
