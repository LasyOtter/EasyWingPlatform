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
import java.util.List;
import java.util.function.Consumer;

/**
 * 生成任务队列抽象（PRD FR-10.1 / FR-10.9）。
 * <p>
 * 存在两种实现，由 {@code easywing.aid.queue.type} 选择：
 * <ul>
 *   <li>{@code redis-stream}（默认）：Redis Stream + 消费组，支持 ACK、PEL、XAUTOCLAIM 故障接管。</li>
 *   <li>{@code local}：进程内 {@code LinkedBlockingQueue} + 线程池，用于无 Redis 环境
 *       与单测（对应 NFR-9「关闭 MQ 后核心链路不中断」的降级形态）。</li>
 * </ul>
 * 无论哪种实现，都保证至少一次投递 + 消费端幂等，因此「重启后任务不重复、不悬挂」
 * （AC-3 / NFR-1）由幂等键与任务状态机共同兜住，而非依赖队列的精确一次语义。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface TaskQueue {

    /**
     * 入队。
     *
     * @param message 任务消息
     * @return 投递标识（Stream 实现返回 entry id；本地实现返回 deliveryId）
     */
    String enqueue(TaskMessage message);

    /**
     * 批量入队，减少往返次数。
     *
     * @param messages 消息列表
     * @return 成功入队条数
     */
    int enqueueBatch(List<TaskMessage> messages);

    /**
     * 注册消费者。同一实现内的多个 consumer 会竞争同一条队列（工作窃取）。
     *
     * @param consumerName 消费者名称，需在同一队列内唯一
     * @param handler      消息处理器；返回 true 表示处理成功可 ACK
     */
    void registerConsumer(String consumerName, Consumer<TaskMessage> handler);

    /**
     * 启动调度：阻塞等待并派发消息。实现应保证单实例内只有一个派发循环。
     */
    void start();

    /**
     * 停止派发，等待在途任务处理完成或超时。
     *
     * @param timeout 优雅停机等待上限
     */
    void shutdown(Duration timeout);

    /**
     * 队列中待处理的消息条数（不含已投递未确认的部分）。
     *
     * @return 待处理条数；无法获取时返回 -1
     */
    long pendingCount();

    /**
     * 已投递但未确认（in-flight）的消息条数，即 PEL 长度。
     *
     * @return 未确认条数；无法获取时返回 -1
     */
    long inFlightCount();

    /**
     * 队列类型标识，用于健康检查与运维排查。
     *
     * @return {@code redis-stream} 或 {@code local}
     */
    String type();
}
