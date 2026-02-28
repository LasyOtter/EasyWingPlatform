package com.easywing.platform.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return Health.down()
                        .withDetail("error", "Redis connection factory is null")
                        .build();
            }

            String pong = connectionFactory.getConnection().ping();

            if ("PONG".equals(pong)) {
                Properties info = connectionFactory.getConnection()
                        .serverCommands()
                        .info();

                if (info != null) {
                    return Health.up()
                            .withDetail("redis_version", info.getProperty("redis_version", "unknown"))
                            .withDetail("connected_clients", info.getProperty("connected_clients", "unknown"))
                            .withDetail("used_memory_human", info.getProperty("used_memory_human", "unknown"))
                            .withDetail("uptime_in_days", info.getProperty("uptime_in_days", "unknown"))
                            .build();
                }
                return Health.up()
                        .withDetail("status", "PONG")
                        .build();
            } else {
                return Health.down()
                        .withDetail("ping_response", pong)
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
