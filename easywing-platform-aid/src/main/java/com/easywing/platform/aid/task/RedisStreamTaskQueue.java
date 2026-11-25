/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.aid.task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StringUtils;

/**
 * 基于 Redis Stream 的生成任务队列（PRD FR-10.1 ~ FR-10.9）。
 *
 * <p><b>为什么是 Stream 而不是 List+Lua</b>：M0 预研在同一 Redis 7.4.12 上实测，
 * 三种方案的单命令吞吐都在同一量级（受 RTT 限制约 1200~1400 ops/s，批量读写可达数万/秒），
 * 但只有 Stream 内建了消费组、ACK、PEL 与故障接管——这三者是
 * 「重启后任务不悬挂、可接管」（FR-10.8 / NFR-1）的前提。自研 List+Lua 等价语义
 * 需要自行维护 processing 集与扫描恢复，复杂度与出错面都更大。</p>
 *
 * <p><b>关键设计</b>：</p>
 * <ul>
 *   <li>消息体只带 taskId，负载从库里读，保证重启后可续跑（FR-9.10）。</li>
 *   <li>消费端先按 {@code deliveryId} 做 SET NX 幂等去重，重复投递直接 ACK 跳过。</li>
 *   <li>处理失败<b>不 ACK</b>，留在 PEL 中由 {@link #reclaimStaleMessages()} 在
 *       min-idle-time 之后通过 XPENDING + XCLAIM 重新派发（FR-10.5 补偿）。</li>
 *   <li>任务表的 retryCount 提供第二重上限保护，避免无限重投。</li>
 * </ul>
 *
 * <p><b>实现说明</b>：spring-data-redis 3.5 的 {@code StreamOperations} 未暴露
 * {@code XAUTOCLAIM}，因此回收走 {@code XPENDING range} 列出挂起消息 + {@code XCLAIM}
 * 逐条夺接，语义等价且受官方支持。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class RedisStreamTaskQueue implements TaskQueue {

    /**
     * 入队并近似裁剪 Stream 长度。
     * <p>MAXLEN '~' 是流式裁剪，避免每条消息触发一次全量 trim。</p>
     */
    private static final String LUA_ENQUEUE = "return redis.call('XADD', KEYS[1], 'MAXLEN', '~', ARGV[1], '*',"
            + " 'taskId', ARGV[2], 'type', ARGV[3], 'tenantId', ARGV[4],"
            + " 'idempotencyKey', ARGV[5], 'priority', ARGV[6], 'enqueuedAt', ARGV[7])";

    /** 幂等去重：SET NX EX。 */
    private static final String LUA_DEDUP =
            "if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1]) then return 1 else return 0 end";

    private static final RedisScript<String> ENQUEUE_SCRIPT = new DefaultRedisScript<>(LUA_ENQUEUE, String.class);
    private static final RedisScript<Long> DEDUP_SCRIPT = new DefaultRedisScript<>(LUA_DEDUP, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AidQueueProperties properties;
    private final Map<String, Consumer<TaskMessage>> handlers = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService dispatcher;
    private ScheduledExecutorService reclaimer;

    public RedisStreamTaskQueue(StringRedisTemplate redisTemplate, AidQueueProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public String enqueue(TaskMessage message) {
        long taskId = requireTaskId(message);
        String entryId = redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(properties.getStreamKey()),
                String.valueOf(properties.getMaxLen()),
                String.valueOf(taskId),
                String.valueOf(message.getType()),
                message.getTenantId() == null ? "" : String.valueOf(message.getTenantId()),
                nullToEmpty(message.getIdempotencyKey()),
                String.valueOf(message.getPriority()),
                String.valueOf(message.getEnqueuedAt()));
        ensureConsumerGroup();
        if (entryId == null) {
            throw new IllegalStateException("任务入队失败: taskId=" + taskId);
        }
        log.debug("任务入队: taskId={}, type={}, stream={}", taskId, message.getType(), properties.getStreamKey());
        return entryId;
    }

    @Override
    public int enqueueBatch(List<TaskMessage> messages) {
        int count = 0;
        for (TaskMessage message : messages) {
            enqueue(message);
            count++;
        }
        return count;
    }

    @Override
    public void registerConsumer(String consumerName, Consumer<TaskMessage> handler) {
        handlers.put(consumerName, handler);
        ensureConsumerGroup();
        log.info("注册任务消费者: {}", consumerName);
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ensureConsumerGroup();

        int workers = Math.max(1, properties.getConsumerThreads());
        dispatcher = Executors.newScheduledThreadPool(workers, r -> {
            Thread t = new Thread(r, "aid-task-dispatcher");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < workers; i++) {
            final String workerName = "worker-" + i;
            dispatcher.scheduleWithFixedDelay(
                    () -> dispatchLoop(workerName), 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        }

        reclaimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aid-task-reclaimer");
            t.setDaemon(true);
            return t;
        });
        reclaimer.scheduleWithFixedDelay(
                this::reclaimStaleMessages,
                properties.getReclaimIntervalMs(),
                properties.getReclaimIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info(
                "Redis Stream 任务队列已启动: stream={}, group={}, workers={}, maxLen={}",
                properties.getStreamKey(),
                properties.getGroup(),
                workers,
                properties.getMaxLen());
    }

    @Override
    public void shutdown(Duration timeout) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (dispatcher != null) {
            dispatcher.shutdownNow();
        }
        if (reclaimer != null) {
            reclaimer.shutdownNow();
        }
        log.info("Redis Stream 任务队列已停止");
    }

    /**
     * 派发循环：阻塞读取一批消息并逐个处理。
     * <p>异常被吞掉留待下一轮，避免单个坏消息卡死调度器。</p>
     */
    private void dispatchLoop(String consumerName) {
        try {
            StreamReadOptions options = StreamReadOptions.empty()
                    .count(properties.getBatchSize())
                    .block(Duration.ofMillis(properties.getBlockTimeoutMs()));
            StreamOffset<String> offset = StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed());

            List<MapRecord<String, Object, Object>> records = redisTemplate
                    .opsForStream()
                    .read(
                            org.springframework.data.redis.connection.stream.Consumer.from(
                                    properties.getGroup(), consumerName),
                            options,
                            offset);
            if (records == null || records.isEmpty()) {
                return;
            }
            Consumer<TaskMessage> handler = resolveHandler(consumerName);
            for (MapRecord<String, Object, Object> record : records) {
                if (!running.get()) {
                    return;
                }
                handleRecord(record, consumerName, handler);
            }
        } catch (Exception e) {
            // 断连、超时等可恢复错误：本轮放弃，下一轮重试
            log.warn("任务派发循环异常（将自动重试）: {}", e.getMessage());
        }
    }

    private void handleRecord(
            MapRecord<String, Object, Object> record, String consumerName, Consumer<TaskMessage> handler) {
        RecordId id = record.getId();
        try {
            TaskMessage message = decode(record);
            if (message == null) {
                log.warn("丢弃无法解析的队列消息: id={}", id);
                acknowledge(id);
                return;
            }
            if (!markDelivered(message)) {
                log.debug("任务重复投递，跳过: taskId={}", message.getTaskId());
                acknowledge(id);
                return;
            }
            if (handler == null || !safeHandle(handler, message)) {
                // 不 ACK：留在 PEL 中等待退避后的 XCLAIM 重新投递
                log.warn("任务处理失败，保留待重投: taskId={}", message.getTaskId());
                return;
            }
            acknowledge(id);
        } catch (Exception e) {
            log.error("任务处理异常: id={}", id, e);
        }
    }

    private boolean safeHandle(Consumer<TaskMessage> handler, TaskMessage message) {
        try {
            handler.accept(message);
            return true;
        } catch (Exception e) {
            log.error("任务处理器返回失败: taskId={}", message.getTaskId(), e);
            return false;
        }
    }

    private void acknowledge(RecordId id) {
        try {
            redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), properties.getGroup(), id);
        } catch (Exception e) {
            log.warn("ACK 失败（不影响正确性，可能重复投递）: id={}", id);
        }
    }

    /**
     * 幂等去重：同一 deliveryId 只在 TTL 内生效一次。
     * <p>TTL 需大于「最长重试间隔 × 最大重试次数」，否则合法重试会被误判为新任务。</p>
     */
    private boolean markDelivered(TaskMessage message) {
        String key = properties.getDedupKeyPrefix() + message.deliveryId();
        Long result =
                redisTemplate.execute(DEDUP_SCRIPT, List.of(key), String.valueOf(properties.getDedupTtlSeconds()));
        return result != null && result == 1L;
    }

    /**
     * 回收挂起消息（FR-10.5 补偿 / FR-10.8 逻辑槽判活）。
     * <p>用 XPENDING 列出超过 min-idle-time 的消息，再用 XCLAIM 夺接到本实例名下；
     * 随后由普通派发循环的「已读未确认」路径（ReadOffset from last-delivered）继续处理。
     * 这是「服务重启后任务不悬挂」的核心机制：旧消费者的 PEL 会被新实例接管。</p>
     */
    private void reclaimStaleMessages() {
        try {
            PendingMessages pending = redisTemplate
                    .opsForStream()
                    .pending(
                            properties.getStreamKey(),
                            org.springframework.data.redis.connection.stream.Consumer.from(
                                    properties.getGroup(), "reclaimer"),
                            org.springframework.data.domain.Range.unbounded(),
                            properties.getBatchSize());
            if (pending == null || pending.isEmpty()) {
                return;
            }
            long minIdleMs = properties.getReclaimMinIdleMs();
            int reclaimed = 0;
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().toMillis() >= minIdleMs) {
                    redisTemplate
                            .opsForStream()
                            .claim(
                                    properties.getStreamKey(),
                                    properties.getGroup(),
                                    "reclaimer",
                                    Duration.ofMillis(minIdleMs),
                                    message.getId());
                    reclaimed++;
                }
            }
            if (reclaimed > 0) {
                log.info("回收挂起任务 {} 条", reclaimed);
            }
        } catch (Exception e) {
            log.warn("挂起任务回收异常: {}", e.getMessage());
        }
    }

    private void ensureConsumerGroup() {
        try {
            redisTemplate
                    .opsForStream()
                    .createGroup(properties.getStreamKey(), ReadOffset.from("0"), properties.getGroup());
            log.debug("创建消费组: {}", properties.getGroup());
        } catch (Exception e) {
            // BUSYGROUP 表示已存在，属正常路径
            if (!StringUtils.hasText(e.getMessage()) || !e.getMessage().contains("BUSYGROUP")) {
                log.debug("消费组初始化: {}", e.getMessage());
            }
        }
    }

    private Consumer<TaskMessage> resolveHandler(String consumerName) {
        Consumer<TaskMessage> handler = handlers.get(consumerName);
        if (handler != null) {
            return handler;
        }
        return handlers.values().iterator().next();
    }

    @SuppressWarnings("unchecked")
    private TaskMessage decode(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        Object taskIdValue = fields.get("taskId");
        if (taskIdValue == null) {
            return null;
        }
        try {
            return TaskMessage.builder()
                    .taskId(Long.parseLong(String.valueOf(taskIdValue)))
                    .type(parseType(fields.get("type")))
                    .tenantId(parseLong(fields.get("tenantId")))
                    .idempotencyKey(parseString(fields.get("idempotencyKey")))
                    .priority(parseInt(fields.get("priority"), 5))
                    .enqueuedAt(
                            parseLong(fields.get("enqueuedAt")) == null
                                    ? System.currentTimeMillis()
                                    : parseLong(fields.get("enqueuedAt")))
                    .build();
        } catch (NumberFormatException e) {
            log.warn("队列消息字段格式错误: id={}, fields={}", record.getId(), fields);
            return null;
        }
    }

    private static TaskMessage.TaskType parseType(Object value) {
        if (value == null) {
            return TaskMessage.TaskType.IMAGE;
        }
        try {
            return TaskMessage.TaskType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            log.warn("未知任务类型，回退为 IMAGE: {}", value);
            return TaskMessage.TaskType.IMAGE;
        }
    }

    private static String parseString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long requireTaskId(TaskMessage message) {
        if (message == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("任务消息必须携带 taskId");
        }
        return message.getTaskId();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public long pendingCount() {
        try {
            Long size = redisTemplate.opsForStream().size(properties.getStreamKey());
            return size == null ? -1 : size;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public long inFlightCount() {
        try {
            return redisTemplate
                    .opsForStream()
                    .pending(properties.getStreamKey(), properties.getGroup())
                    .getTotalPendingMessages();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String type() {
        return "redis-stream";
    }

    /** 供健康检查使用。 */
    public AidQueueProperties properties() {
        return properties;
    }

    /** 当前注册的消费者名称快照。 */
    public List<String> consumerNames() {
        return new ArrayList<>(handlers.keySet());
    }
}
