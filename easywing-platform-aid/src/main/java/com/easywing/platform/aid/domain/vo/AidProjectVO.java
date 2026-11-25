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
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 创作项目出参。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "创作项目出参")
public class AidProjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目ID")
    private Long id;

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目简介")
    private String description;

    @Schema(description = "封面图 URL")
    private String coverUrl;

    @Schema(description = "关联的视觉设定 ID")
    private Long visualSettingId;

    @Schema(description = "视觉设定名称（联表带出，避免前端二次查询）")
    private String visualSettingName;

    @Schema(description = "项目状态")
    private String status;

    @Schema(description = "目标总时长（秒）")
    private Integer targetDuration;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "分集数量")
    private Integer episodeCount;

    @Schema(description = "资产数量")
    private Integer assetCount;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
