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
package com.easywing.platform.aid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

/**
 * 生成任务入参（PRD FR-9 / FR-10，M1 只落库与查询，调度在 M2）。
 * <p>
 * 提交时即固化 {@code prompt} 快照与视觉快照版本：队列消息只带 taskId，
 * 因此进程重启后仍能按同一份提示词续跑（FR-9.10），也才能对历史产物做审计回放（NFR-13）。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "生成任务入参")
public class AidGenerationTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联对象类型：SHOT/ASSET/EPISODE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "关联对象类型不能为空")
    @Size(max = 30, message = "关联对象类型长度不能超过30个字符")
    private String targetType;

    @Schema(description = "关联对象ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "关联对象ID不能为空") private Long targetId;

    @Schema(description = "任务类型：IMAGE/VIDEO/AUDIO/COMPOSE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 20, message = "任务类型长度不能超过20个字符")
    private String taskType;

    @Schema(description = "使用的 Provider")
    @Size(max = 50, message = "Provider 长度不能超过50个字符")
    private String provider;

    @Schema(description = "使用的模型标识")
    @Size(max = 100, message = "模型标识长度不能超过100个字符")
    private String model;

    @Schema(description = "最终提交的提示词快照；为空表示由服务编译后再落库")
    private String prompt;

    @Schema(description = "提交时固化的视觉快照版本")
    private Integer visualSnapshotVersion;
}
