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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 视觉设定入参（PRD FR-4.7 / FR-11，AC-11）。
 * <p>
 * 列表字段直接以 {@code List<String>} 承接，与库内 JSON 列互转；尾帧规范展开为独立字段，
 * 避免前端拼 JSON。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "视觉设定入参")
public class AidVisualSettingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "设定ID（修改时必填）")
    private Long id;

    @Schema(description = "设定名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "视觉设定名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100个字符")
    private String name;

    @Schema(description = "作用域：PROJECT/TEMPLATE，默认 PROJECT")
    @Pattern(regexp = "^(PROJECT|TEMPLATE)?$", message = "作用域只能是 PROJECT 或 TEMPLATE")
    private String scope;

    @Schema(description = "风格描述主文本（硬约束层内容锚）")
    @Size(max = 1000, message = "风格描述长度不能超过1000个字符")
    private String styleDescription;

    @Schema(description = "色盘声明（中文+十六进制），逐字引用到提示词")
    private List<String> colorPalette;

    @Schema(description = "子风格标签族")
    private List<String> subStyles;

    @Schema(description = "约束条件")
    private List<String> constraintsList;

    @Schema(description = "禁用词/负向清单")
    private List<String> negativeWords;

    @Schema(description = "画幅，如 9:16 / 16:9")
    @Pattern(regexp = "^(|\\d{1,2}:\\d{1,2})$", message = "画幅格式应为 9:16 这样的比例")
    private String aspectRatio;

    @Schema(description = "尾帧规范·主体")
    private String tailFrameSubject;

    @Schema(description = "尾帧规范·背景")
    private String tailFrameBackground;

    @Schema(description = "尾帧规范·光线方向")
    private String tailFrameLight;

    @Schema(description = "尾帧规范·构图")
    private String tailFrameComposition;

    @Schema(description = "尾帧规范·氛围")
    private String tailFrameMood;

    @Schema(description = "尾帧必须出现的颜色名")
    private List<String> tailFrameColors;
}
