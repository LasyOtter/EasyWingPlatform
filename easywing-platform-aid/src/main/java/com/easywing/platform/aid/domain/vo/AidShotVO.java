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
 * 分镜出参。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "分镜出参")
public class AidShotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分镜ID")
    private Long id;

    @Schema(description = "所属分集ID")
    private Long episodeId;

    @Schema(description = "镜号")
    private Integer shotNo;

    @Schema(description = "画面描述")
    private String description;

    @Schema(description = "台词")
    private String dialogue;

    @Schema(description = "镜头语言")
    private String cameraMovement;

    @Schema(description = "时长（秒）")
    private Integer duration;

    @Schema(description = "分镜图 URL")
    private String imageUrl;

    @Schema(description = "视频 URL")
    private String videoUrl;

    @Schema(description = "最终提示词（审计用）")
    private String promptFinal;

    @Schema(description = "状态")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
