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
package com.easywing.platform.aid.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 生成任务出参（PRD FR-9 / FR-10）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "生成任务出参")
public class AidGenerationTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "任务类型：IMAGE/VIDEO/AUDIO/COMPOSE")
    private String taskType;

    @Schema(description = "关联对象类型")
    private String targetType;

    @Schema(description = "关联对象ID")
    private Long targetId;

    @Schema(description = "Provider")
    private String provider;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "提示词快照")
    private String prompt;

    @Schema(description = "幂等键")
    private String idempotencyKey;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "进度百分比 0-100")
    private Integer progress;

    @Schema(description = "产物 URL")
    private String resultUrl;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "实际费用")
    private BigDecimal costAmount;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "Redis Stream entryId")
    private String queueEntryId;

    @Schema(description = "提交时固化的视觉快照版本")
    private Integer visualSnapshotVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始执行时间")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
