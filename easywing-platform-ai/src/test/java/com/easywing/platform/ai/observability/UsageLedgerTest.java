package com.easywing.platform.ai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.observability.store.InMemoryUsageLedgerStore;
import org.junit.jupiter.api.Test;

class UsageLedgerTest {

    @Test
    void aggregatesTokensAndCostByModel() {
        UsageLedger ledger = ledger(100);
        ledger.record(record("u1", "t1", "openai", "gpt-4o", 100, 50, 1.0, 1L));
        ledger.record(record("u2", "t2", "openai", "gpt-4o", 200, 100, 2.0, 2L));
        ledger.record(record("u2", "t2", "openai", "gpt-4o-mini", 10, 5, 0.1, 3L));

        UsageLedger.UsageReport report = ledger.buildReport();

        assertThat(report.getSummary().getRequestCount()).isEqualTo(3);
        assertThat(report.getSummary().getTotalTokens()).isEqualTo(465);
        assertThat(report.getSummary().getTotalCostUsd()).isCloseTo(3.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(report.getByModel()).hasSize(2);
        assertThat(report.getByModel())
                .extracting(UsageLedger.ModelUsage::getModel)
                .containsExactly("gpt-4o", "gpt-4o-mini");
        assertThat(report.getByModel().get(0).getTotalTokens()).isEqualTo(450);
    }

    @Test
    void filtersRecordsByUserAndTenant() {
        UsageLedger ledger = ledger(100);
        ledger.record(record("alice", "tenant-a", "openai", "gpt-4o", 10, 5, 0.1, 1L));
        ledger.record(record("bob", "tenant-b", "openai", "gpt-4o", 20, 10, 0.2, 2L));
        ledger.record(record("alice", "tenant-b", "openai", "gpt-4o", 40, 20, 0.4, 3L));

        assertThat(ledger.getUserRecords("alice", null)).hasSize(2);
        assertThat(ledger.getUserRecords("alice", "tenant-a")).hasSize(1);
        assertThat(ledger.getUserRecords("bob", "tenant-a")).isEmpty();

        UsageLedger.UsageReport aliceTenantA = ledger.buildUserReport("alice", "tenant-a");
        assertThat(aliceTenantA.getSummary().getRequestCount()).isEqualTo(1);
        assertThat(aliceTenantA.getSummary().getTotalTokens()).isEqualTo(15);
        assertThat(ledger.buildUserReport("nobody", null).getSummary().getRequestCount())
                .isZero();
    }

    @Test
    void trimsOldestRecordsBeyondCapacity() {
        // 容量下限为 100，因此用 101 触发淘汰
        UsageLedger ledger = ledger(101);
        for (int i = 1; i <= 105; i++) {
            ledger.record(record("u1", "t1", "openai", "gpt-4o", i, i, 0.0, i));
        }

        assertThat(ledger.getRecords()).hasSize(101);
        assertThat(ledger.getRecords()).extracting(UsageRecord::getPromptTokens).startsWith(105, 104, 103, 102, 101);
    }

    @Test
    void clearRemovesAllRecords() {
        UsageLedger ledger = ledger(100);
        ledger.record(record("u1", "t1", "openai", "gpt-4o", 1, 1, 0.0, 1L));
        ledger.clear();

        assertThat(ledger.getRecords()).isEmpty();
        assertThat(ledger.buildReport().getSummary().getRequestCount()).isZero();
    }

    @Test
    void fillsMissingIdAndTimestamp() {
        UsageLedger ledger = ledger(100);
        UsageRecord saved =
                ledger.record(UsageRecord.builder().userId("u1").model("gpt-4o").build());

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getTimestamp()).isPositive();
    }

    private UsageLedger ledger(int maxRecords) {
        return new UsageLedger(new InMemoryUsageLedgerStore(maxRecords));
    }

    private UsageRecord record(
            String userId,
            String tenantId,
            String provider,
            String model,
            int prompt,
            int completion,
            double cost,
            long ts) {
        return UsageRecord.builder()
                .userId(userId)
                .tenantId(tenantId)
                .provider(provider)
                .model(model)
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(prompt + completion)
                .estimatedCostUsd(cost)
                .timestamp(ts)
                .build();
    }
}
