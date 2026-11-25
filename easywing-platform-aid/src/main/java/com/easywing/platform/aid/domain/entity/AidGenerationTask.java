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
package com.easywing.platform.aid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生成任务（PRD FR-9 / FR-10）。
 * <p>
 * 任务行只承载调度与审计信息；真实负载（提示词、视觉快照）在提交时固化到
 * {@link #prompt} 与 {@link #visualSnapshotVersion}，使队列消息可以只带 taskId，
 * 进程重启后仍能从库里续跑（FR-9.10）。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aid_generation_task")
public class AidGenerationTask extends BaseEntity {

    /**
     * 任务类型：IMAGE/VIDEO/AUDIO/COMPOSE。
     * @see com.easywing.platform.aid.task.TaskMessage.TaskType
     */
    private String taskType;

    /** 关联对象类型：SHOT/ASSET/EPISODE */
    private String targetType;

    /** 关联对象 ID */
    private Long targetId;

    /** 使用的 Provider */
    private String provider;

    /** 使用的模型标识 */
    private String model;

    /** 最终提交的提示词快照 */
    private String prompt;

    /** 幂等键：项目:剧集:逻辑任务类型:目标（FR-10.7 原子防重） */
    private String idempotencyKey;

    /**
     * 状态。
     * @see com.easywing.platform.aid.enums.TaskStatus
     */
    private String status;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 产物 URL */
    private String resultUrl;

    /** 失败原因 */
    private String errorMessage;

    /** 实际费用 */
    private java.math.BigDecimal costAmount;

    /** 已重试次数 */
    private Integer retryCount;

    /** Redis Stream entryId，用于对账 */
    private String queueEntryId;

    /** 提交时固化的视觉快照版本 */
    private Integer visualSnapshotVersion;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
