package com.easywing.platform.ai.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Agent 追踪 Redis 存储
 * <p>
 * Hash（{@code ai:trace}）保存追踪快照本体，ZSET（{@code ai:trace:ts}）按开始时间维护顺序，
 * 用于按 {@code easywing.ai.trace.max-records} 淘汰最旧记录。
 * 多实例读写同一份数据，管理员查询到的是全局 Agent 执行链路。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.trace", name = "storage-type", havingValue = "redis")
public class RedisTraceStore implements TraceStore {

    private static final String TRACE_KEY = "ai:trace";
    private static final String TRACE_TS_KEY = "ai:trace:ts";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxRecords;

    public RedisTraceStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${easywing.ai.trace.max-records:1000}") int maxRecords) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxRecords = Math.max(100, maxRecords);
    }

    @Override
    public void save(AgentTrace trace) {
        if (trace == null || trace.getTraceId() == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(TRACE_KEY, trace.getTraceId(), objectMapper.writeValueAsString(trace));
            redisTemplate.opsForZSet().add(TRACE_TS_KEY, trace.getTraceId(), trace.getStartTime());
            trim();
        } catch (Exception e) {
            log.error("Failed to persist agent trace: {}", trace.getTraceId(), e);
        }
    }

    private void trim() {
        Long size = redisTemplate.opsForZSet().zCard(TRACE_TS_KEY);
        if (size == null || size <= maxRecords) {
            return;
        }
        long excess = size - maxRecords;
        Set<String> oldest = redisTemplate.opsForZSet().range(TRACE_TS_KEY, 0, excess - 1);
        if (oldest == null || oldest.isEmpty()) {
            return;
        }
        redisTemplate.opsForHash().delete(TRACE_KEY, oldest.toArray());
        redisTemplate.opsForZSet().remove(TRACE_TS_KEY, oldest.toArray());
        log.debug("Trace store trimmed {} oldest traces", oldest.size());
    }

    @Override
    public Optional<AgentTrace> load(String traceId) {
        Object value = redisTemplate.opsForHash().get(TRACE_KEY, traceId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value.toString(), AgentTrace.class));
        } catch (Exception e) {
            log.error("Failed to deserialize agent trace: {}", traceId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<AgentTrace> loadAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(TRACE_KEY);
        List<AgentTrace> traces = new ArrayList<>(entries.size());
        for (Object value : entries.values()) {
            try {
                traces.add(objectMapper.readValue(value.toString(), AgentTrace.class));
            } catch (Exception e) {
                log.error("Failed to deserialize agent trace", e);
            }
        }
        return traces;
    }

    @Override
    public void delete(String traceId) {
        redisTemplate.opsForHash().delete(TRACE_KEY, traceId);
        redisTemplate.opsForZSet().remove(TRACE_TS_KEY, traceId);
    }

    @Override
    public void clear() {
        redisTemplate.delete(TRACE_KEY);
        redisTemplate.delete(TRACE_TS_KEY);
    }
}
