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
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

/**
 * 创作项目入参。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "创作项目入参")
public class AidProjectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 修改时必填；新增时为空 */
    @Schema(description = "项目ID（修改时必填）")
    private Long id;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100个字符")
    private String name;

    @Schema(description = "项目简介")
    @Size(max = 500, message = "项目简介长度不能超过500个字符")
    private String description;

    @Schema(description = "封面图 URL")
    @Size(max = 500, message = "封面图 URL 长度不能超过500个字符")
    private String coverUrl;

    @Schema(description = "关联的视觉设定 ID，可后续改绑")
    private Long visualSettingId;

    @Schema(description = "项目状态：DRAFT/RUNNING/PAUSED/COMPLETED/ARCHIVED")
    private String status;

    @Schema(description = "目标总时长（秒）")
    private Integer targetDuration;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
