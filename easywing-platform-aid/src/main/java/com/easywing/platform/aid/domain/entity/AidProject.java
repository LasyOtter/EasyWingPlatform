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
 * 创作项目（PRD FR-1 ~ FR-3）。
 * <p>
 * 项目是 AID 的最高聚合根：分集、资产、分镜、生成任务都挂在项目下。
 * 视觉设定通过 {@link #visualSettingId} 关联，允许「先建项目后配风格」以及多项目复用同一模板。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aid_project")
public class AidProject extends BaseEntity {

    /** 项目名称 */
    private String name;

    /** 项目简介 */
    private String description;

    /** 封面图 URL */
    private String coverUrl;

    /** 关联的视觉设定 ID */
    private Long visualSettingId;

    /**
     * 项目状态。
     * @see com.easywing.platform.aid.enums.ProjectStatus
     */
    private String status;

    /** 目标总时长（秒） */
    private Integer targetDuration;

    /** 备注 */
    private String remark;
}
