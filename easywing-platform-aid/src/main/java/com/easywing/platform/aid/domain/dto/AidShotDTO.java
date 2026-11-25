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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

/**
 * 分镜入参（PRD FR-8）。
 * <p>
 * M1 只负责录入与维护；{@code promptFinal} 由 M2 的生成流程回填，不接受前端传入——
 * 它是审计产物，前端改写会让「历史提示词可回放」失去意义。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "分镜入参")
public class AidShotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分镜ID（修改时必填）")
    private Long id;

    @Schema(description = "所属分集ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分集ID不能为空") private Long episodeId;

    @Schema(description = "镜号，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "镜号不能为空") @Min(value = 1, message = "镜号必须大于0")
    private Integer shotNo;

    @Schema(description = "画面描述，可含 @资产[C01] 占位符")
    private String description;

    @Schema(description = "台词")
    private String dialogue;

    @Schema(description = "镜头语言，如 跟拍/推近/固定")
    @Size(max = 200, message = "镜头语言长度不能超过200个字符")
    private String cameraMovement;

    @Schema(description = "时长（秒）")
    @Min(value = 0, message = "时长不能为负数")
    private Integer duration;

    @Schema(description = "状态：DRAFT/GENERATING/COMPLETED/FAILED")
    private String status;
}
