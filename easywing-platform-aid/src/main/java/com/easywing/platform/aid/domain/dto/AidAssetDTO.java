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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

/**
 * 资产库入参（PRD FR-6，AC-11 资产编号自定义）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "资产库入参")
public class AidAssetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "资产ID（修改时必填）")
    private Long id;

    @Schema(description = "所属项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目ID不能为空") private Long projectId;

    /**
     * 资产编号，项目内唯一。允许字母、数字、下划线与短横线——用户会按习惯写
     * {@code C01} / {@code S01} / {@code P01}，限制成纯数字或固定前缀反而会挡住正常用法。
     */
    @Schema(description = "资产编号，如 C01/S01/P01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资产编号不能为空")
    @Size(max = 50, message = "资产编号长度不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "资产编号只能包含字母、数字、下划线或短横线")
    private String assetCode;

    @Schema(description = "资产类型：CHARACTER/SCENE/PROP/STYLE")
    private String assetType;

    @Schema(description = "资产名称")
    @Size(max = 100, message = "资产名称长度不能超过100个字符")
    private String name;

    @Schema(description = "资产描述")
    @Size(max = 1000, message = "资产描述长度不能超过1000个字符")
    private String description;

    @Schema(description = "参考图 URL")
    @Size(max = 500, message = "参考图 URL 长度不能超过500个字符")
    private String referenceUrl;

    @Schema(description = "该资产专属提示词模板，可覆盖项目级设定")
    private String promptTemplate;
}
