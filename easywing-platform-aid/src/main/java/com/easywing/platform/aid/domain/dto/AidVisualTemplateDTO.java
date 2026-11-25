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

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 视觉设定模板导入入参（AC-11「新项目可导入模板」）。
 * <p>
 * 直接收 {@link JsonNode} 而不是强类型 DTO：导出的 JSON 可能来自旧版本，
 * 字段增删不应导致导入失败——这里按 {@code schemaVersion} 做兼容判断，未知字段忽略。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Schema(description = "视觉设定模板导入入参")
public class AidVisualTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 导入后的模板名称；为空时沿用导出内容里的 name */
    @Schema(description = "导入后的模板名称，留空则沿用导出内容中的名称")
    private String name;

    /** 导出时生成的 JSON 全文 */
    @Schema(description = "视觉设定模板 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode content;

    /** 是否覆盖同名模板，默认 false（同名则报错，避免误覆盖） */
    @Schema(description = "同名时是否覆盖，默认 false")
    private Boolean overwrite;
}
