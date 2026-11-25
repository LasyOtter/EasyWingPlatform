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
 * 分集入参。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "分集入参")
public class AidEpisodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分集ID（修改时必填）")
    private Long id;

    @Schema(description = "所属项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目ID不能为空") private Long projectId;

    @Schema(description = "集号，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "集号不能为空") @Min(value = 1, message = "集号必须大于0")
    private Integer episodeNo;

    @Schema(description = "分集标题")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @Schema(description = "剧本文本")
    private String script;

    @Schema(description = "目标时长（秒）")
    private Integer duration;

    @Schema(description = "状态：DRAFT/GENERATING/COMPLETED/FAILED")
    private String status;
}
