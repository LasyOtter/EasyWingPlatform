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
package com.easywing.platform.data.interceptor;

import com.baomidou.mybatisplus.core.plugin.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.easywing.platform.data.properties.DataProperties;
import com.easywing.platform.data.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.HashSet;
import java.util.Set;

/**
 * 多租户SQL拦截处理器
 * <p>
 * 自动在SQL中添加租户ID过滤条件，支持忽略特定表
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class TenantLineInnerInterceptor implements TenantLineHandler {

    private final DataProperties dataProperties;

    private final Set<String> ignoreTables;

    public TenantLineInnerInterceptor(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
        this.ignoreTables = new HashSet<>(dataProperties.getTenantIgnoreTables());
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("TenantId is not set, query will not be filtered by tenant");
            return new LongValue(0L);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return dataProperties.getTenantIdColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 检查是否启用了租户支持
        if (!dataProperties.isTenantEnabled()) {
            return true;
        }

        // 检查是否在忽略列表中
        if (ignoreTables.contains(tableName)) {
            log.debug("Ignore tenant filter for table: {}", tableName);
            return true;
        }

        // 检查是否通过MP注解忽略
        if (InterceptorIgnoreHelper.ignoreTenant(tableName)) {
            return true;
        }

        return false;
    }
}
