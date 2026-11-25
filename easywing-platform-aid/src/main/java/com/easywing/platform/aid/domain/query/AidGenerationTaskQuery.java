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
package com.easywing.platform.aid.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 生成任务分页过滤条件（PRD FR-9 / FR-10）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "生成任务分页过滤条件")
public class AidGenerationTaskQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联对象类型：SHOT/ASSET/EPISODE")
    private String targetType;

    @Schema(description = "关联对象ID")
    private Long targetId;

    @Schema(description = "任务类型：IMAGE/VIDEO/AUDIO/COMPOSE")
    private String taskType;

    @Schema(description = "状态：PENDING/RUNNING/COMPLETED/FAILED/CANCELED")
    private String status;

    @Schema(description = "幂等键，精确匹配")
    private String idempotencyKey;
}
