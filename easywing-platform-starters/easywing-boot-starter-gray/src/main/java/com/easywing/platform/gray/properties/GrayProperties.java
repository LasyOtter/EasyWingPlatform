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
package com.easywing.platform.gray.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 灰度发布配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.gray")
public class GrayProperties {

    /**
     * 是否启用灰度发布
     */
    private boolean enabled = false;

    /**
     * 灰度版本号
     */
    private String version = "1.0.0";

    /**
     * 灰度权重 (0-100)
     */
    private int weight = 10;

    /**
     * 灰度规则
     */
    private Map<String, String> rules = new HashMap<>();

    /**
     * 灰度规则类型
     */
    public enum RuleType {
        HEADER,    // 基于请求头
        PARAMETER, // 基于请求参数
        COOKIE,    // 基于 Cookie
        USER_ID,   // 基于用户ID
        IP         // 基于IP地址
    }
}
