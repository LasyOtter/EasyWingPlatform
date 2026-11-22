package com.easywing.platform.ai.observability;

import com.easywing.platform.ai.observability.store.UsageLedgerStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Token 用量与成本账本。
 * <p>
 * 记录每次模型调用的 Token 数与估算成本，支持按用户/租户过滤以及按模型聚合。
 * 持久化委托给 {@link UsageLedgerStore}：内存实现供本地开发，Redis 实现让多实例共享同一份账本。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UsageLedger {

    private final UsageLedgerStore store;

    public UsageLedger(UsageLedgerStore store) {
        this.store = store;
    }

    /**
     * 记录一次用量，自动生成缺失的 ID 与时间戳。
     */
    public UsageRecord record(UsageRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId(UUID.randomUUID().toString());
        }
        if (record.getTimestamp() == 0) {
            record.setTimestamp(System.currentTimeMillis());
        }
        store.save(record);
        return record;
    }

    /**
     * 全部记录，按时间倒序。
     */
    public List<UsageRecord> getRecords() {
        return sortedRecords(store.loadAll());
    }

    /**
     * 指定用户（可选租户）的记录，按时间倒序。
     */
    public List<UsageRecord> getUserRecords(String userId, String tenantId) {
        return sortedRecords(store.loadAll().stream()
                .filter(r -> matchesUser(r, userId, tenantId))
                .toList());
    }

    /**
     * 平台整体用量报告。
     */
    public UsageReport buildReport() {
        return buildReport(store.loadAll());
    }

    /**
     * 指定用户（可选租户）的用量报告。
     */
    public UsageReport buildUserReport(String userId, String tenantId) {
        return buildReport(store.loadAll().stream()
                .filter(r -> matchesUser(r, userId, tenantId))
                .toList());
    }

    /**
     * 清空账本（管理员操作）。
     */
    public void clear() {
        store.clear();
    }

    private UsageReport buildReport(Iterable<UsageRecord> source) {
        List<UsageRecord> list = new ArrayList<>();
        source.forEach(list::add);

        UsageSummary summary = new UsageSummary();
        summary.setRequestCount(list.size());
        for (UsageRecord record : list) {
            summary.setPromptTokens(summary.getPromptTokens() + record.getPromptTokens());
            summary.setCompletionTokens(summary.getCompletionTokens() + record.getCompletionTokens());
            summary.setTotalTokens(summary.getTotalTokens() + record.getTotalTokens());
            summary.setTotalCostUsd(summary.getTotalCostUsd() + record.getEstimatedCostUsd());
        }

        Map<String, ModelUsage> byModel = new TreeMap<>();
        for (UsageRecord record : list) {
            String key = record.getProvider() + "/" + record.getModel();
            byModel.compute(key, (k, acc) -> {
                ModelUsage usage =
                        acc != null ? acc : new ModelUsage(record.getProvider(), record.getModel(), 0, 0, 0, 0, 0.0);
                usage.setRequestCount(usage.getRequestCount() + 1);
                usage.setPromptTokens(usage.getPromptTokens() + record.getPromptTokens());
                usage.setCompletionTokens(usage.getCompletionTokens() + record.getCompletionTokens());
                usage.setTotalTokens(usage.getTotalTokens() + record.getTotalTokens());
                usage.setTotalCostUsd(usage.getTotalCostUsd() + record.getEstimatedCostUsd());
                return usage;
            });
        }

        return new UsageReport(summary, List.copyOf(byModel.values()));
    }

    private boolean matchesUser(UsageRecord record, String userId, String tenantId) {
        if (userId != null && !userId.equals(record.getUserId())) {
            return false;
        }
        return tenantId == null || tenantId.equals(record.getTenantId());
    }

    private List<UsageRecord> sortedRecords(Iterable<UsageRecord> source) {
        List<UsageRecord> list = new ArrayList<>();
        source.forEach(list::add);
        list.sort(Comparator.comparingLong(UsageRecord::getTimestamp).reversed());
        return list;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageSummary {
        private long requestCount;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double totalCostUsd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelUsage {
        private String provider;
        private String model;
        private long requestCount;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double totalCostUsd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageReport {
        private UsageSummary summary;
        private List<ModelUsage> byModel;
    }
}
