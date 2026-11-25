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
package com.easywing.platform.aid.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 资产库分页过滤条件。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "资产库分页过滤条件")
public class AidAssetQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目ID（资产接口必传，避免跨项目遍历）")
    private Long projectId;

    @Schema(description = "资产类型：CHARACTER/SCENE/PROP/STYLE")
    private String assetType;

    @Schema(description = "资产编号或名称，模糊匹配")
    private String keyword;
}
