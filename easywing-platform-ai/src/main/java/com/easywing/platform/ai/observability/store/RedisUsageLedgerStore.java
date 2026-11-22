package com.easywing.platform.ai.observability.store;

import com.easywing.platform.ai.observability.UsageRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用量账本 Redis 存储
 * <p>
 * Hash（{@code ai:usage}）保存记录本体，ZSET（{@code ai:usage:ts}）按时间戳维护顺序，
 * 用于按 {@code easywing.ai.usage.max-records} 淘汰最旧记录。
 * 多实例读写同一份数据，管理员看到的成本账本是全局视图。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.usage", name = "storage-type", havingValue = "redis")
public class RedisUsageLedgerStore implements UsageLedgerStore {

    private static final String USAGE_KEY = "ai:usage";
    private static final String USAGE_TS_KEY = "ai:usage:ts";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxRecords;

    public RedisUsageLedgerStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${easywing.ai.usage.max-records:10000}") int maxRecords) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxRecords = Math.max(100, maxRecords);
    }

    @Override
    public void save(UsageRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(USAGE_KEY, record.getId(), objectMapper.writeValueAsString(record));
            redisTemplate.opsForZSet().add(USAGE_TS_KEY, record.getId(), record.getTimestamp());
            trim();
        } catch (Exception e) {
            log.error("Failed to persist usage record: {}", record.getId(), e);
        }
    }

    private void trim() {
        Long size = redisTemplate.opsForZSet().zCard(USAGE_TS_KEY);
        if (size == null || size <= maxRecords) {
            return;
        }
        long excess = size - maxRecords;
        Set<String> oldest = redisTemplate.opsForZSet().range(USAGE_TS_KEY, 0, excess - 1);
        if (oldest == null || oldest.isEmpty()) {
            return;
        }
        redisTemplate.opsForHash().delete(USAGE_KEY, oldest.toArray());
        redisTemplate.opsForZSet().remove(USAGE_TS_KEY, oldest.toArray());
        log.debug("Usage ledger trimmed {} oldest records", oldest.size());
    }

    @Override
    public List<UsageRecord> loadAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(USAGE_KEY);
        List<UsageRecord> records = new ArrayList<>(entries.size());
        for (Object value : entries.values()) {
            try {
                records.add(objectMapper.readValue(value.toString(), UsageRecord.class));
            } catch (Exception e) {
                log.error("Failed to deserialize usage record", e);
            }
        }
        return records;
    }

    @Override
    public void delete(String id) {
        redisTemplate.opsForHash().delete(USAGE_KEY, id);
        redisTemplate.opsForZSet().remove(USAGE_TS_KEY, id);
    }

    @Override
    public void clear() {
        redisTemplate.delete(USAGE_KEY);
        redisTemplate.delete(USAGE_TS_KEY);
    }
}
