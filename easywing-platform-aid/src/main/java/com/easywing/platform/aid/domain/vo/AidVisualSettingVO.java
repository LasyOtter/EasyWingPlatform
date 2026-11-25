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
import java.util.List;
import lombok.Data;

/**
 * 视觉设定出参（AC-11）。
 * <p>
 * 库内的 JSON 列在此还原为 {@code List<String>}，前端可直接绑定到色盘编辑器；
 * 尾帧规范也展开为独立字段，不让前端去解析嵌套对象。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "视觉设定出参")
public class AidVisualSettingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "设定ID")
    private Long id;

    @Schema(description = "设定名称")
    private String name;

    @Schema(description = "作用域：PROJECT/TEMPLATE")
    private String scope;

    @Schema(description = "风格描述主文本")
    private String styleDescription;

    @Schema(description = "色盘声明")
    private List<String> colorPalette;

    @Schema(description = "子风格标签族")
    private List<String> subStyles;

    @Schema(description = "约束条件")
    private List<String> constraintsList;

    @Schema(description = "禁用词/负向清单")
    private List<String> negativeWords;

    @Schema(description = "画幅")
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

    @Schema(description = "快照时间戳（毫秒）")
    private Long snapshotTime;

    @Schema(description = "版本号")
    private Integer version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
