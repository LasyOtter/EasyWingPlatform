package com.easywing.platform.system.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class UserMetrics {

    private final MeterRegistry meterRegistry;

    public void recordUserOperation(String operation, String operator) {
        String operatorRole = getOperatorRole(operator);
        meterRegistry.counter("user.operation",
                "operation", operation,
                "operator_role", operatorRole
        ).increment();
    }

    public Timer.Sample startUserQuery() {
        return Timer.start(meterRegistry);
    }

    public void recordUserQuery(Timer.Sample sample, String queryType, int resultCount) {
        sample.stop(meterRegistry.timer("user.query.duration",
                "query_type", queryType,
                "result_size", resultSizeBucket(resultCount)));
    }

    public <T> T recordDbOperation(String operation, Supplier<T> supplier) {
        return meterRegistry.timer("db.operation", "operation", operation)
                .record(supplier);
    }

    public void recordDbOperation(String operation, Runnable runnable) {
        meterRegistry.timer("db.operation", "operation", operation)
                .record(runnable);
    }

    private String getOperatorRole(String operator) {
        if (operator == null || "system".equals(operator)) {
            return "system";
        }
        return "user";
    }

    private String resultSizeBucket(int count) {
        if (count == 0) return "0";
        if (count <= 10) return "1-10";
        if (count <= 50) return "11-50";
        if (count <= 100) return "51-100";
        return "100+";
    }
}
