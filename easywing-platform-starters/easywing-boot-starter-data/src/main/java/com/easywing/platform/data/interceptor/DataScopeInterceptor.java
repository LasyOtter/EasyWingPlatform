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

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.easywing.platform.data.datascope.DataScopeContext;
import com.easywing.platform.data.properties.DataProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Set;

/**
 * 数据权限拦截器
 * <p>
 * 根据用户角色的数据权限范围自动在SQL中添加过滤条件，防止水平越权访问。
 * 支持5种数据权限类型：
 * - ALL: 全部数据权限
 * - DEPT_ONLY: 本部门数据权限
 * - DEPT_AND_CHILD: 本部门及以下数据权限
 * - SELF_ONLY: 仅本人数据权限
 * - CUSTOM: 自定义数据权限
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class DataScopeInterceptor implements InnerInterceptor {

    private final DataScopeHandler dataScopeHandler;
    private final DataScopeSqlParser dataScopeSqlParser;

    public DataScopeInterceptor(DataProperties dataProperties, DataScopeHandler dataScopeHandler) {
        this.dataScopeHandler = dataScopeHandler;
        this.dataScopeSqlParser = new DataScopeSqlParser(dataProperties);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        applyDataScope(ms, boundSql);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        BoundSql boundSql = ms.getBoundSql(parameter);
        applyDataScope(ms, boundSql);
    }

    private void applyDataScope(MappedStatement ms, BoundSql boundSql) {
        if (DataScopeContext.isIgnoreDataScope()) {
            log.debug("Data scope check is ignored for this request");
            return;
        }

        if (InterceptorIgnoreHelper.ignoreDataScope(ms.getId())) {
            return;
        }

        DataScopeInfo dataScopeInfo = dataScopeHandler.getDataScopeInfo();
        if (dataScopeInfo == null) {
            log.debug("No data scope info available, skipping data scope filter");
            return;
        }

        if (dataScopeInfo.isAdmin()) {
            log.debug("Admin user, skipping data scope filter");
            return;
        }

        if (dataScopeInfo.getDataScope() == DataScopeType.ALL) {
            return;
        }

        try {
            boolean applied = dataScopeSqlParser.applyDataScope(boundSql, ms, dataScopeInfo);
            if (applied) {
                log.debug("Data scope filter applied: userId={}, scope={}, sql={}",
                        dataScopeInfo.getUserId(), dataScopeInfo.getDataScope(), boundSql.getSql());
            }
        } catch (DataScopeSqlException ex) {
            log.error("Failed to apply data scope filter", ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to apply data scope filter", ex);
            throw new DataScopeSqlException("数据权限过滤失败", ex);
        }
    }

    /**
     * 数据权限类型枚举
     */
    public enum DataScopeType {
        ALL,            // 全部数据权限
        CUSTOM,         // 自定义数据权限
        DEPT_ONLY,      // 本部门数据权限
        DEPT_AND_CHILD, // 本部门及以下数据权限
        SELF_ONLY       // 仅本人数据权限
    }

    /**
     * 数据权限信息接口
     */
    public interface DataScopeInfo {
        Long getUserId();
        Long getDeptId();
        DataScopeType getDataScope();
        boolean isAdmin();
        Set<Long> getChildDeptIds();
        List<Long> getCustomDeptIds();
    }

    /**
     * 数据权限处理器接口
     */
    public interface DataScopeHandler {
        DataScopeInfo getDataScopeInfo();
    }
}
