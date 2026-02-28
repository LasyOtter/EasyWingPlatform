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
package com.easywing.platform.feign.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.feign")
public class FeignProperties {

    /**
     * 是否启用 Feign 增强配置
     */
    private boolean enabled = true;

    /**
     * 连接超时时间(毫秒)
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间(毫秒)
     */
    private int readTimeout = 10000;

    /**
     * 是否启用请求日志
     */
    private boolean loggingEnabled = true;

    /**
     * 日志级别: NONE, BASIC, HEADERS, FULL
     */
    private String logLevel = "BASIC";

    /**
     * 是否传递请求头(如 trace-id, auth-token)
     */
    private boolean propagateHeaders = true;

    /**
     * 需要传递的请求头列表
     */
    private String[] propagateHeaderNames = {
        "X-Request-Id", "X-Trace-Id", "X-Span-Id",
        "Authorization", "X-Tenant-Id"
    };
}
