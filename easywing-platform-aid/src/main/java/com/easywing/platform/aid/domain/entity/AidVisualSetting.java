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
package com.easywing.platform.aid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视觉设定（PRD FR-4.7 / FR-11，AC-11）。
 * <p>
 * 存储结构与 {@link com.easywing.platform.aid.prompt.VisualSnapshot} 一一对应，
 * 是快照在库内的落地形态。{@code scope=TEMPLATE} 的记录可被导出为 JSON 模板并导入到新项目
 * （AC-11 的后两条验收项）。
 * </p>
 * <p>
 * 色盘等列表字段以 JSON 列存储，读取时反序列化为 List；这样既保留顺序（色盘顺序即渲染顺序），
 * 又避免为数组单独建表。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aid_visual_setting")
public class AidVisualSetting extends BaseEntity {

    /** 设定名称 */
    private String name;

    /**
     * 作用域：PROJECT（项目级）/ TEMPLATE（可复用模板）。
     * @see com.easywing.platform.aid.enums.SettingScope
     */
    private String scope;

    /** 风格描述主文本（硬约束层的内容锚） */
    private String styleDescription;

    /** 色盘声明（JSON 数组），逐字引用到每条提示词（FR-11.6） */
    private String colorPalette;

    /** 子风格标签族（JSON 数组），用于长提示词段落强化（FR-11.7） */
    private String subStyles;

    /** 约束条件（JSON 数组） */
    private String constraintsList;

    /** 禁用词/负向清单（JSON 数组） */
    private String negativeWords;

    /** 画幅，如 9:16 / 16:9 */
    private String aspectRatio;

    /** 尾帧规范·主体 */
    private String tailFrameSubject;

    /** 尾帧规范·背景 */
    private String tailFrameBackground;

    /** 尾帧规范·光线方向 */
    private String tailFrameLight;

    /** 尾帧规范·构图 */
    private String tailFrameComposition;

    /** 尾帧规范·氛围 */
    private String tailFrameMood;

    /** 尾帧必须出现的颜色名（JSON 数组） */
    private String tailFrameColors;

    /** 快照时间戳（毫秒），0 表示未设置 */
    private Long snapshotTime;

    /** 快照版本号，每次修改 +1；下游产物记录该版本以判定是否需要失效 */
    private Integer version;
}
