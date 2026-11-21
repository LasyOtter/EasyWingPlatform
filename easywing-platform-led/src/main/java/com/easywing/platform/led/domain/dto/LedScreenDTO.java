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
package com.easywing.platform.led.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

/**
 * 屏元数据写入请求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class LedScreenDTO implements Serializable {

    private Long id;

    @NotBlank(message = "屏ID不能为空")
    private String screenId;

    private String screenName;

    @NotBlank(message = "控制器地址不能为空")
    private String ip;

    @NotNull(message = "屏宽不能为空") private Integer width;

    @NotNull(message = "屏高不能为空") private Integer height;

    private Integer colorType;
    private Integer grayLevel;
    private Integer ledType;
    private String sdkProvider;
    private String connection;
    private String remark;
}
