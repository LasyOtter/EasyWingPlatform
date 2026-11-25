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

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成任务消息体（PRD FR-10）。
 * <p>
 * 队列中只传任务标识与幂等键，真实负载（提示词、快照）从数据库读取——
 * 避免大字段在 Redis 中反复搬运，也让「进程重启后续跑复用已建 task_id」成为默认行为
 * （FR-9.10 异步任务恢复）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {

    /** 平台内部任务 ID（落库主键） */
    private Long taskId;

    /** 幂等键：{@code 项目:剧集:逻辑任务类型:目标}（FR-10.7 原子防重） */
    private String idempotencyKey;

    /** 任务类型：IMAGE / VIDEO / AUDIO / COMPOSE */
    private TaskType type;

    /** 优先级，数值小先执行 */
    @Builder.Default
    private int priority = 5;

    /** 所属租户，消费端用于隔离与计费 */
    private Long tenantId;

    /** 入队时间戳（毫秒），用于积压告警与延迟统计；0 表示未设置，由消费端回退为当前时间 */
    @Builder.Default
    private long enqueuedAt = 0L;

    /**
     * 生成消息的唯一投递 ID。
     * <p>消费端用它做 SET NX 去重（NFR-3 幂等性）：同一实例重复投递只执行一次。</p>
     *
     * @return 稳定的投递标识
     */
    public String deliveryId() {
        return taskId != null ? "task-" + taskId : "task-" + UUID.randomUUID();
    }

    /** 任务类型枚举。 */
    public enum TaskType {
        /** 分镜图生成 */
        IMAGE,
        /** 视频片段生成 */
        VIDEO,
        /** 配音生成 */
        AUDIO,
        /** FFmpeg 成片合成 */
        COMPOSE
    }

    /**
     * 创建一条入队消息，自动补齐入队时间。
     *
     * @param taskId         任务主键
     * @param idempotencyKey 幂等键
     * @param type           任务类型
     * @param tenantId       租户
     * @return 消息体
     */
    public static TaskMessage of(Long taskId, String idempotencyKey, TaskType type, Long tenantId) {
        return TaskMessage.builder()
                .taskId(taskId)
                .idempotencyKey(idempotencyKey)
                .type(type)
                .tenantId(tenantId)
                .enqueuedAt(Instant.now().toEpochMilli())
                .build();
    }
}
