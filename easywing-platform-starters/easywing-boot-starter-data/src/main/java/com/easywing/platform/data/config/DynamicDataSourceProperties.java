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
package com.easywing.platform.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 动态数据源配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.datasource.dynamic")
public class DynamicDataSourceProperties {

    /**
     * 是否启用动态数据源
     */
    private boolean enabled = false;

    /**
     * 是否启用从库（读写分离）
     */
    private boolean slaveEnabled = false;

    /**
     * 主库配置
     */
    private Master master = new Master();

    /**
     * 从库配置
     */
    private Slave slave = new Slave();

    @Data
    public static class Master {
        /**
         * 是否启用
         */
        private boolean enabled = true;
    }

    @Data
    public static class Slave {
        /**
         * 是否启用
         */
        private boolean enabled = false;
    }
}
