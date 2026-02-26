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
package com.easywing.platform.data.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据访问配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.data")
public class DataProperties {

    /**
     * 是否启用多租户支持
     */
    private boolean tenantEnabled = false;

    /**
     * 多租户忽略表列表（系统表等不需要租户过滤的表）
     */
    private List<String> tenantIgnoreTables = new ArrayList<>();

    /**
     * 租户ID字段名
     */
    private String tenantIdColumn = "tenant_id";

    /**
     * 是否启用审计字段自动填充
     */
    private boolean auditEnabled = true;

    /**
     * 审计字段配置
     */
    private Audit audit = new Audit();

    /**
     * 分页配置
     */
    private Pagination pagination = new Pagination();

    /**
     * 审计字段配置
     */
    @Data
    public static class Audit {
        /**
         * 创建时间字段名
         */
        private String createTimeColumn = "create_time";

        /**
         * 更新时间字段名
         */
        private String updateTimeColumn = "update_time";

        /**
         * 创建人字段名
         */
        private String createByColumn = "create_by";

        /**
         * 更新人字段名
         */
        private String updateByColumn = "update_by";

        /**
         * 逻辑删除字段名
         */
        private String deletedColumn = "deleted";

        /**
         * 租户ID字段名
         */
        private String tenantIdColumn = "tenant_id";
    }

    /**
     * 分页配置
     */
    @Data
    public static class Pagination {
        /**
         * 单页最大记录数
         */
        private long maxLimit = 1000;

        /**
         * 是否允许溢出页码
         */
        private boolean overflow = true;
    }
}
