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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 进程内任务队列（PRD FR-10.9「无 MQ 降级」/NFR-9）。
 * <p>
 * 用于没有 Redis 的环境、单测与本地开发。优先级队列保证高优先级任务先执行，
 * 处理失败的消息进入退避重试队列而非直接丢弃，因此核心链路（生成 → 合成）
 * 在不依赖任何外部中间件时仍可完成。
 *
 * <p><b>局限</b>：不跨实例、不持久化。生产环境必须使用
 * {@link RedisStreamTaskQueue}；本类不应被当作集群方案。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class LocalTaskQueue implements TaskQueue {

    /** 最大重试次数，超出即放弃（与 Redis Stream 侧的 retryCount 上限对应）。 */
    private static final int MAX_RETRIES = 5;

    /** 默认优先级，与 {@code TaskMessage#priority} 保持一致。 */
    private static final int DEFAULT_PRIORITY = 5;

    /**
     * 单位优先级差的排序键步长。
     * <p>必须远小于相邻两次入队的序号间隔（1），否则会跨位、破坏 FIFO；默认 0.01，
     * 即最大 10 级优先级也只前移 0.1 个位次。</p>
     */
    private static final double PRIORITY_STEP = 0.01;

    /** 任务完成后仍抑制同 deliveryId 重复投递的时长（毫秒），用于吸收回执丢失造成的重投。 */
    private static final long DONE_SUPPRESS_MS = 5_000L;

    /** 按排序键升序派发：值越小越先。 */
    private final PriorityBlockingQueue<QueuedItem> ready =
            new PriorityBlockingQueue<>(1024, Comparator.comparingDouble(item -> item.orderKey));

    /** 退避重试队列：到点后重新入队。键为 taskId，避免同一任务重复入队。 */
    private final ConcurrentHashMap<Long, DelayedItem> delayed = new ConcurrentHashMap<>();

    /** 各任务已重试次数，用于指数退避与上限保护。 */
    private final ConcurrentHashMap<Long, Integer> retryCounts = new ConcurrentHashMap<>();

    /**
     * 正在执行的投递标识。
     * <p>仅依赖这个标记有一个实测踩到的漏洞：任务成功返回后标记立即清除，而队列里还压着它的重复副本，
     * 副本随后被取出时检查已通过，于是又执行一遍。因此额外用 {@link #doneTimes} 记录完成时刻，
     * 在 {@link #DONE_SUPPRESS_MS} 窗口内吸收「回执丢失造成的重投」。</p>
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** 各 deliveryId 的最近成功完成时刻（毫秒时间戳），仅在抑制窗口内有效。 */
    private final ConcurrentHashMap<String, Long> doneTimes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Consumer<TaskMessage>> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService dispatcher;
    private Thread retryPump;

    /**
     * 入队序号：单调递增，<b>数值越小表示越早入队</b>。
     * <p>从 0 开始自增而非从 {@code Long.MIN_VALUE} 递减——后者会在溢出后反转为最大值，
     * 直接把队列顺序打乱（这个坑已经踩过一次）。</p>
     */
    private long enqueueSeq;

    @Override
    public synchronized String enqueue(TaskMessage message) {
        if (message == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("任务消息必须携带 taskId");
        }
        long sequence = enqueueSeq++;
        ready.add(new QueuedItem(message, orderKey(sequence, message.getPriority())));
        return message.deliveryId();
    }

    /**
     * 计算排序键：<b>入队次序为主序，优先级只做同一位次内的微调</b>。
     * <p>{@code sequence} 越小表示越早入队；高优先级（priority 数值小）通过减去一个远小于 1 的
     * 偏移抢到前面，但不会跨过前一个任务的位次，从而既保证 FIFO 又让紧急任务插队。</p>
     *
     * @param sequence 单调递增的入队序号
     * @param priority 优先级，数值越小越紧急
     * @return 排序键，越小越先派发
     */
    private static double orderKey(long sequence, int priority) {
        int normalized = Math.max(0, Math.min(priority, 10));
        return sequence - (DEFAULT_PRIORITY - normalized) * PRIORITY_STEP;
    }

    @Override
    public synchronized int enqueueBatch(List<TaskMessage> messages) {
        // 一次性取出同一段连续序列，保证批量任务的相对次序与原子性
        int count = 0;
        long firstSeq = enqueueSeq;
        for (TaskMessage message : messages) {
            if (message == null || message.getTaskId() == null) {
                throw new IllegalArgumentException("任务消息必须携带 taskId");
            }
            // 批量内的相对次序即列表顺序：靠前的元素序号更小，orderKey 更小（更先派发）
            ready.add(new QueuedItem(message, orderKey(firstSeq + count, message.getPriority())));
            count++;
        }
        enqueueSeq += count;
        return count;
    }

    @Override
    public void registerConsumer(String consumerName, Consumer<TaskMessage> handler) {
        handlers.put(consumerName, handler);
        log.info("注册本地任务消费者: {}", consumerName);
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // 本地队列是单消费者模型：派发顺序、优先级与去重都依赖「一次只处理一条」这个前提。
        // 多 worker 会同时取走多条消息，FIFO 与在途幂等都会失去意义（生产环境用 Redis Stream 的消费组）。
        dispatcher = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "aid-local-queue");
            t.setDaemon(true);
            return t;
        });
        dispatcher.submit(this::dispatchLoop);

        retryPump = new Thread(this::retryLoop, "aid-local-retry");
        retryPump.setDaemon(true);
        retryPump.start();

        log.info("本地任务队列已启动（单消费者）");
    }

    @Override
    public void shutdown(Duration timeout) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (dispatcher != null) {
            dispatcher.shutdownNow();
        }
        if (retryPump != null) {
            retryPump.interrupt();
        }
        log.info("本地任务队列已停止");
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                QueuedItem item = ready.take();
                if (!running.get()) {
                    ready.offer(item);
                    return;
                }
                String deliveryId = item.message.deliveryId();
                if (!beginDelivery(deliveryId)) {
                    continue;
                }
                boolean success = false;
                try {
                    Consumer<TaskMessage> handler = handlers.values().iterator().next();
                    success = safeHandle(handler, item.message);
                } finally {
                    finishDelivery(deliveryId, success);
                }
                if (!success) {
                    scheduleRetry(item);
                } else {
                    retryCounts.remove(item.message.getTaskId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 尝试取得投递权。
     *
     * @param deliveryId 投递标识
     * @return 可执行返回 {@code true}；正在执行或处于完成后抑制窗口返回 {@code false}
     */
    private synchronized boolean beginDelivery(String deliveryId) {
        if (inFlight.contains(deliveryId)) {
            log.debug("任务正在执行中，跳过重复投递: deliveryId={}", deliveryId);
            return false;
        }
        Long doneAt = doneTimes.get(deliveryId);
        if (doneAt != null && System.currentTimeMillis() - doneAt < DONE_SUPPRESS_MS) {
            log.debug("任务刚完成，跳过重复投递: deliveryId={}", deliveryId);
            return false;
        }
        inFlight.add(deliveryId);
        return true;
    }

    /**
     * 结束投递。
     *
     * @param deliveryId 投递标识
     * @param success    处理器是否成功；仅成功才写入完成时刻
     */
    private void finishDelivery(String deliveryId, boolean success) {
        inFlight.remove(deliveryId);
        if (success) {
            doneTimes.put(deliveryId, System.currentTimeMillis());
            // 顺带清理过期条目，避免长期运行无界增长
            long now = System.currentTimeMillis();
            doneTimes.entrySet().removeIf(entry -> now - entry.getValue() >= DONE_SUPPRESS_MS);
        }
    }

    private boolean safeHandle(Consumer<TaskMessage> handler, TaskMessage message) {
        try {
            handler.accept(message);
            return true;
        } catch (Exception e) {
            log.error("本地队列任务处理失败: taskId={}", message.getTaskId(), e);
            return false;
        }
    }

    /**
     * 计算退避延迟。
     * <p>按已重试次数指数增长（2s、4s、8s…）并设上限；首次失败用短延迟，避免测试与交互场景等待过久。</p>
     *
     * @param retryCount 已重试次数
     * @return 毫秒
     */
    private long backoffDelayMs(int retryCount) {
        long delay = 2_000L * (1L << Math.min(retryCount, 4));
        return Math.min(delay, 30_000L);
    }

    private void scheduleRetry(QueuedItem item) {
        int retryCount = retryCounts.merge(item.message.getTaskId(), 1, Integer::sum);
        long delayMs = backoffDelayMs(retryCount - 1);
        long dueAt = System.currentTimeMillis() + delayMs;
        delayed.put(item.message.getTaskId(), new DelayedItem(item.message, dueAt, retryCount));
        log.info("任务进入退避重试: taskId={}, retryCount={}, delayMs={}", item.message.getTaskId(), retryCount, delayMs);
    }

    private void retryLoop() {
        while (running.get()) {
            try {
                Thread.sleep(1_000L);
                long now = System.currentTimeMillis();
                delayed.entrySet().removeIf(entry -> {
                    DelayedItem delayedItem = entry.getValue();
                    if (delayedItem.dueAt > now) {
                        return false;
                    }
                    int retryCount = delayedItem.retryCount;
                    if (retryCount >= MAX_RETRIES) {
                        log.error("任务重试 {} 次仍失败，已放弃: taskId={}", retryCount, entry.getKey());
                        retryCounts.remove(entry.getKey());
                        return true;
                    }
                    // 重投排到当前队尾，避免持续抢占新任务
                    synchronized (this) {
                        // 重投排到队首：失败任务需要尽快重试，而不是等所有新任务跑完
                        ready.offer(new QueuedItem(delayedItem.message, orderKey(enqueueSeq--, DEFAULT_PRIORITY)));
                    }
                    return true;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public long pendingCount() {
        return ready.size();
    }

    @Override
    public long inFlightCount() {
        return delayed.size();
    }

    @Override
    public String type() {
        return "local";
    }

    /** 待派发项；orderKey 为排序键，越大越先派发。 */
    private record QueuedItem(TaskMessage message, double orderKey) {}

    /** 退避中的重试项。 */
    private record DelayedItem(TaskMessage message, long dueAt, int retryCount) {}
}
