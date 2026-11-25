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
 * 资产库出参。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "资产库出参")
public class AidAssetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "资产ID")
    private Long id;

    @Schema(description = "所属项目ID")
    private Long projectId;

    @Schema(description = "资产编号")
    private String assetCode;

    @Schema(description = "资产类型")
    private String assetType;

    @Schema(description = "资产名称")
    private String name;

    @Schema(description = "资产描述")
    private String description;

    @Schema(description = "参考图 URL")
    private String referenceUrl;

    @Schema(description = "该资产专属提示词模板")
    private String promptTemplate;

    @Schema(description = "版本号")
    private Integer version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
