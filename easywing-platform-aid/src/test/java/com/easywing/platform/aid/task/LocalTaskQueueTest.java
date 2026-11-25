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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 本地任务队列测试（FR-10.9 降级模式 / NFR-3 幂等性）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class LocalTaskQueueTest {

    private LocalTaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new LocalTaskQueue();
    }

    @AfterEach
    void tearDown() {
        queue.shutdown(Duration.ofSeconds(1));
    }

    @Test
    void dispatchesEnqueuedMessages() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<Long> received = new CopyOnWriteArrayList<>();
        queue.registerConsumer("test", message -> received.add(message.getTaskId()));
        queue.start();

        queue.enqueue(TaskMessage.of(1L, "k:1", TaskMessage.TaskType.IMAGE, 10L));
        assertTrue(latch.await(2, TimeUnit.SECONDS) || !received.isEmpty());
        assertTrue(waitUntil(() -> received.contains(1L), 3), "未收到任务: " + received);
    }

    /**
     * 优先级在<b>空闲队列</b>上生效：低优先级任务已被取走执行时，高优先级应抢占下一个空位，
     * 而不是重排正在运行的任务。
     */
    @Test
    void higherPriorityWinsTheNextFreeSlot() throws Exception {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        queue.registerConsumer("test", message -> {
            order.add(message.getPriority());
            if (order.size() == 1) {
                firstStarted.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            done.countDown();
        });
        queue.start();

        TaskMessage low = TaskMessage.builder()
                .taskId(1L)
                .priority(9)
                .type(TaskMessage.TaskType.IMAGE)
                .build();
        TaskMessage high = TaskMessage.builder()
                .taskId(2L)
                .priority(1)
                .type(TaskMessage.TaskType.IMAGE)
                .build();
        queue.enqueue(low);
        assertTrue(firstStarted.await(3, TimeUnit.SECONDS), "首个任务未被派发");
        queue.enqueue(high);
        release.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(2, order.size());
        assertEquals(Integer.valueOf(1), order.get(1), "高优先级应占用释放后的第一个槽位: " + order);
    }

    /** 同一优先级的并发入队保持 FIFO。 */
    @Test
    void samePriorityPreservesFifoOrder() throws Exception {
        int count = 20;
        List<Long> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(count);
        queue.registerConsumer("test", message -> {
            order.add(message.getTaskId());
            done.countDown();
        });
        queue.start();

        for (long i = 1; i <= count; i++) {
            queue.enqueue(TaskMessage.of(i, "fifo:" + i, TaskMessage.TaskType.IMAGE, 1L));
        }

        assertTrue(done.await(8, TimeUnit.SECONDS), "仍有任务未派发: " + order.size());
        assertEquals(count, order.size());
        for (int i = 1; i <= count; i++) {
            assertEquals(Long.valueOf(i), order.get(i - 1), "第 " + i + " 个位置顺序错乱: " + order);
        }
    }

    /**
     * NFR-3 / FR-10.7：同一任务在途期间重复投递不应重复执行。
     * <p>幂等窗口是「处理器首次成功返回之前」——这正是上游重试、崩溃接管时真正会重叠的区间；
     * 成功后再重复投递属新任务（重做场景），不应被永久抑制。</p>
     */
    @Test
    void deduplicatesWhileInFlightAndAllowsNewAttemptAfterSuccess() throws Exception {
        CountDownLatch blocking = new CountDownLatch(1);
        CountDownLatch duplicatedEnqueued = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        queue.registerConsumer("test", message -> {
            if (counter.getAndIncrement() == 0) {
                blocking.countDown();
                try {
                    // 模拟耗时任务：等重复投递确实入队后再放行，确保重叠窗口真实发生
                    duplicatedEnqueued.await(3, TimeUnit.SECONDS);
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        queue.start();

        TaskMessage message = TaskMessage.of(7L, "idem:7", TaskMessage.TaskType.VIDEO, 1L);
        queue.enqueue(message);
        assertTrue(blocking.await(3, TimeUnit.SECONDS), "首个任务未被派发");
        queue.enqueue(message);
        queue.enqueue(message);
        duplicatedEnqueued.countDown();
        release.countDown();

        // 原任务完成后，之前压在队列里的两份重复副本才被取出——必须被完成抑制窗口挡掉
        assertTrue(waitUntil(() -> counter.get() >= 1, 3));
        Thread.sleep(1_000);
        assertEquals(1, counter.get(), "任务在途期间的重复投递不应重复执行");

        // 换一个 deliveryId 即视为新任务（不同幂等键），应立即执行——证明去重未退化为全局抑制
        queue.enqueue(TaskMessage.of(8L, "idem:8", TaskMessage.TaskType.VIDEO, 1L));
        assertTrue(waitUntil(() -> counter.get() >= 2, 5), "新任务的投递应被允许");
    }

    @Test
    void failedMessageGoesToRetryInsteadOfBeingDropped() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttempt = new CountDownLatch(1);
        queue.registerConsumer("test", message -> {
            if (attempts.incrementAndGet() == 1) {
                firstAttempt.countDown();
                throw new IllegalStateException("模拟失败");
            }
        });
        queue.start();

        queue.enqueue(TaskMessage.of(3L, "retry:3", TaskMessage.TaskType.AUDIO, 1L));
        assertTrue(firstAttempt.await(3, TimeUnit.SECONDS));
        assertTrue(waitUntil(() -> attempts.get() >= 2, 8), "失败任务未被重试, attempts=" + attempts.get());
    }

    @Test
    void reportsPendingAndInFlightCounts() {
        assertEquals(0, queue.pendingCount());
        queue.enqueue(TaskMessage.of(1L, "a", TaskMessage.TaskType.IMAGE, 1L));
        queue.enqueue(TaskMessage.of(2L, "b", TaskMessage.TaskType.IMAGE, 1L));
        assertEquals(2, queue.pendingCount());
        assertEquals("local", queue.type());
    }

    @Test
    void batchEnqueuePreservesCount() {
        int count = queue.enqueueBatch(List.of(
                TaskMessage.of(1L, "a", TaskMessage.TaskType.IMAGE, 1L),
                TaskMessage.of(2L, "b", TaskMessage.TaskType.VIDEO, 1L),
                TaskMessage.of(3L, "c", TaskMessage.TaskType.COMPOSE, 1L)));
        assertEquals(3, count);
        assertEquals(3, queue.pendingCount());
    }

    @Test
    void taskMessageBuildsStableDeliveryId() {
        TaskMessage message = TaskMessage.of(42L, "k", TaskMessage.TaskType.IMAGE, 1L);
        assertEquals("task-42", message.deliveryId());
        assertEquals("task-42", message.deliveryId());
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, int timeoutSeconds) {
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    @Test
    void shutdownStopsDispatchLoop() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            queue.start();
            queue.shutdown(Duration.ofMillis(100));
        });
    }
}
