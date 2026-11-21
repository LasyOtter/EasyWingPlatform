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
package com.easywing.platform.led.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LED 屏元数据（数据库侧）。
 *
 * <p>与 YAML 的关系：屏的「物理参数 + SDK 选择」可落库管理，
 * 节目/区域明细仍以 {@code config/screens/<id>.yml} 为权威来源，
 * 落库的 {@link #programSnapshot} 仅作变更留痕与快速预览。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("led_screen")
public class LedScreen extends BaseEntity {

    /** 屏 ID（与 YAML 文件名一致）。 */
    private String screenId;
    /** 屏名称。 */
    private String screenName;
    /** 控制器地址：局域网 IP / 广域网 MAC 或 ID / 串口 "COM2:115200"。 */
    private String ip;
    /** 屏宽（像素）。 */
    private Integer width;
    /** 屏高（像素）。 */
    private Integer height;
    /** 基色：1 单色, 2 双基色, 3 三基色, 4 X 系列。 */
    private Integer colorType;
    /** 灰度等级。 */
    private Integer grayLevel;
    /** 卡型号：0 T/A/U/XC/W, 1 E, 2 X, 3 C, 4 E5/E6/C8, 5 D2/D4/D6。 */
    private Integer ledType;
    /** SDK 实现：vision / listen / onbon；空表示取根配置默认。 */
    private String sdkProvider;
    /** 连接方式：tcp / udp / serial / wan。 */
    private String connection;
    /** 最后下发结果：0 成功, 1 部分失败, 2 全部失败。 */
    private Integer lastDeployStatus;
    /** 最后下发时间。 */
    private java.time.LocalDateTime lastDeployTime;
    /** 最后错误信息。 */
    private String lastError;
    /** 是否在线（最近一次探测）。 */
    private Integer online;
    /** 节目配置快照（JSON），用于审计与预览。 */
    private String programSnapshot;
    /** 备注。 */
    private String remark;
}
