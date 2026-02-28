package com.easywing.platform.observability.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                if (dataSource instanceof HikariDataSource hikari) {
                    HikariPoolMXBean poolMXBean = hikari.getHikariPoolMXBean();
                    if (poolMXBean != null) {
                        int active = poolMXBean.getActiveConnections();
                        int idle = poolMXBean.getIdleConnections();
                        int pending = poolMXBean.getThreadsAwaitingConnection();

                        return Health.up()
                                .withDetail("database", "MySQL")
                                .withDetail("active_connections", active)
                                .withDetail("idle_connections", idle)
                                .withDetail("pending_threads", pending)
                                .build();
                    }
                }
                return Health.up()
                        .withDetail("database", "MySQL")
                        .withDetail("status", "available")
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Connection validation failed")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
