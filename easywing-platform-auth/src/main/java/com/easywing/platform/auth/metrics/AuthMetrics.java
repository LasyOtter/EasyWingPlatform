package com.easywing.platform.auth.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthMetrics {

    private final MeterRegistry meterRegistry;

    public void recordLoginSuccess(String username, String authType) {
        meterRegistry
                .counter(
                        "auth.login.success",
                        "auth_type",
                        authType,
                        "hour",
                        String.valueOf(LocalDateTime.now().getHour()))
                .increment();
    }

    public void recordLoginFailure(String username, String reason) {
        meterRegistry
                .counter(
                        "auth.login.failure",
                        "reason",
                        reason,
                        "username",
                        username != null ? maskUsername(username) : "unknown",
                        "hour",
                        String.valueOf(LocalDateTime.now().getHour()))
                .increment();
    }

    public Timer.Sample startTokenIssuance() {
        return Timer.start(meterRegistry);
    }

    public void recordTokenIssuance(Timer.Sample sample, String tokenType) {
        sample.stop(meterRegistry.timer("auth.token.issuance", "token_type", tokenType));
    }

    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
