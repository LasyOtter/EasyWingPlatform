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
 * 命名数据源（数据库侧，与 {@code led.yml} 的 sources 段对应）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("led_data_source")
public class LedDataSource extends BaseEntity {

    /** 数据源 ID。 */
    private String sourceId;
    /** 类型：jdbc / http / counter / now / static / script。 */
    private String type;
    /** 连接参数（JSON，含 jdbcUrl/sql/url/headers 等）。 */
    private String params;
    /** 备注。 */
    private String remark;
}
