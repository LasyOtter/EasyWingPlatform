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
 * 内码刷新绑定（数据库侧，与 {@code config/refresh/<id>.yml} 对应）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("led_refresh_binding")
public class LedRefreshBinding extends BaseEntity {

    /** 屏 ID。 */
    private String screenId;
    /** 目标节目号（可空，默认 0）。 */
    private Integer programNo;
    /** 内码区顺序号 0 起。 */
    private Integer areaIndex;
    /** 颜色名。 */
    private String color;
    /** %disp 特效 0-9。 */
    private Integer effect;
    /** 文本模板，支持 {占位符}。 */
    private String template;
    /** 数据源：命名数据源 id，或内建类型名。 */
    private String source;
    /** 刷新周期，如 30s / 1m。 */
    private String interval;
    /** 排序号。 */
    private Integer orderNum;
    /** 是否启用。 */
    private Integer enabled;
    /** counter 源起始值。 */
    private java.math.BigDecimal counterStart;
    /** counter 源步长。 */
    private java.math.BigDecimal counterStep;
    /** counter 源小数位。 */
    private Integer counterDecimals;
    /** now 源格式。 */
    private String nowPattern;
}
