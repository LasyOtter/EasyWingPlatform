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
 * 资产库（PRD FR-6 / FR-11）。
 * <p>
 * {@code assetCode}（如 C01/S01/P01）是提示词里 {@code @资产[C01]} 引用的键，
 * 项目内唯一——由唯一索引 {@code uk_project_code} 保证，同时是 AC-11「资产编号支持自定义」的落点。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aid_asset")
public class AidAsset extends BaseEntity {

    /** 所属项目 ID */
    private Long projectId;

    /** 资产编号，项目内唯一 */
    private String assetCode;

    /**
     * 资产类型。
     * @see com.easywing.platform.aid.enums.AssetType
     */
    private String assetType;

    /** 资产名称 */
    private String name;

    /** 资产描述（供模型理解的人物/场景特征） */
    private String description;

    /** 参考图 URL */
    private String referenceUrl;

    /** 该资产专属提示词模板，可覆盖项目级设定 */
    private String promptTemplate;

    /** 版本号 */
    private Integer version;
}
