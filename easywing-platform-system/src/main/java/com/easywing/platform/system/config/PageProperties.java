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
package com.easywing.platform.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分页配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.page")
public class PageProperties {

    /**
     * 最大页码
     */
    private int maxPage = 1000;

    /**
     * 默认每页大小
     */
    private int defaultSize = 10;

    /**
     * 最大每页大小
     */
    private int maxSize = 100;

    /**
     * 深度分页阈值（超过此页码优化count查询）
     */
    private int deepPageThreshold = 100;

    /**
     * 是否启用count查询缓存
     */
    private boolean countCacheEnabled = true;

    /**
     * count查询缓存时间（秒）
     */
    private int countCacheSeconds = 60;
}
