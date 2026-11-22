package com.easywing.platform.ai.observability.store;

import com.easywing.platform.ai.observability.UsageRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 用量账本内存存储
 * <p>
 * 当 {@code easywing.ai.usage.storage-type} 不是 {@code redis} 时生效。
 * 重启丢失，多实例各自独立，禁止用于生产。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.usage",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryUsageLedgerStore implements UsageLedgerStore {

    private final Map<String, UsageRecord> records = new ConcurrentHashMap<>();
    private final int maxRecords;

    public InMemoryUsageLedgerStore(@Value("${easywing.ai.usage.max-records:10000}") int maxRecords) {
        this.maxRecords = Math.max(100, maxRecords);
        log.warn("AI 用量账本使用内存存储（easywing.ai.usage.storage-type=memory）：" + "重启即丢失，集群下各实例独立，禁止用于生产");
    }

    @Override
    public void save(UsageRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        records.put(record.getId(), record);
        trim();
    }

    private void trim() {
        if (records.size() <= maxRecords) {
            return;
        }
        List<String> expired = records.values().stream()
                .sorted(Comparator.comparingLong(UsageRecord::getTimestamp))
                .limit(records.size() - maxRecords)
                .map(UsageRecord::getId)
                .toList();
        expired.forEach(records::remove);
        log.debug("Usage ledger trimmed {} oldest records", expired.size());
    }

    @Override
    public List<UsageRecord> loadAll() {
        return List.copyOf(records.values());
    }

    @Override
    public void delete(String id) {
        records.remove(id);
    }

    @Override
    public void clear() {
        records.clear();
    }
}
