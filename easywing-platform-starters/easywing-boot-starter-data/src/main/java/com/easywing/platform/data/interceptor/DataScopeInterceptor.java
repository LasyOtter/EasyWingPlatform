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
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final DataProperties dataProperties;
    private final DataScopeHandler dataScopeHandler;

    public DataScopeInterceptor(DataProperties dataProperties, DataScopeHandler dataScopeHandler) {
        this.dataProperties = dataProperties;
        this.dataScopeHandler = dataScopeHandler;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {

        // 检查是否忽略数据权限
        if (DataScopeContext.isIgnoreDataScope()) {
            log.debug("Data scope check is ignored for this request");
            return;
        }

        // 检查是否通过MP注解忽略
        if (InterceptorIgnoreHelper.ignoreDataScope(ms.getId())) {
            return;
        }

        // 获取当前用户数据权限信息
        DataScopeInfo dataScopeInfo = dataScopeHandler.getDataScopeInfo();
        if (dataScopeInfo == null) {
            log.debug("No data scope info available, skipping data scope filter");
            return;
        }

        // 超级管理员跳过数据权限
        if (dataScopeInfo.isAdmin()) {
            log.debug("Admin user, skipping data scope filter");
            return;
        }

        // 全部数据权限，不需要过滤
        if (dataScopeInfo.getDataScope() == DataScopeType.ALL) {
            return;
        }

        // 构建数据权限过滤条件
        Expression dataScopeFilter = buildDataScopeFilter(dataScopeInfo);
        if (dataScopeFilter == null) {
            return;
        }

        // 修改原始SQL，添加权限过滤
        try {
            String originalSql = boundSql.getSql();
            Statement statement = CCJSqlParserUtil.parse(originalSql);

            if (statement instanceof Select) {
                addDataScopeToSelect((Select) statement, dataScopeFilter);
            } else if (statement instanceof Update) {
                addDataScopeToUpdate((Update) statement, dataScopeFilter);
            } else if (statement instanceof Delete) {
                addDataScopeToDelete((Delete) statement, dataScopeFilter);
            }

            // 替换SQL
            String newSql = statement.toString();
            replaceSql(boundSql, newSql);

            log.debug("Data scope filter applied: userId={}, scope={}, sql={}",
                    dataScopeInfo.getUserId(), dataScopeInfo.getDataScope(), newSql);
        } catch (Exception e) {
            log.error("Failed to apply data scope filter", e);
            throw new RuntimeException("数据权限过滤失败", e);
        }
    }

    /**
     * 构建数据权限过滤表达式
     *
     * @param dataScopeInfo 数据权限信息
     * @return SQL过滤表达式
     */
    private Expression buildDataScopeFilter(DataScopeInfo dataScopeInfo) {
        switch (dataScopeInfo.getDataScope()) {
            case DEPT_ONLY:
                // dept_id = 当前部门
                return new EqualsTo(
                        new Column(dataProperties.getDeptIdColumn()),
                        new LongValue(dataScopeInfo.getDeptId())
                );

            case DEPT_AND_CHILD:
                // dept_id IN (当前部门及所有子部门)
                Set<Long> childDeptIds = dataScopeInfo.getChildDeptIds();
                if (childDeptIds == null || childDeptIds.isEmpty()) {
                    childDeptIds = Set.of(dataScopeInfo.getDeptId());
                }

                ExpressionList expressionList = new ExpressionList();
                expressionList.setExpressions(
                        childDeptIds.stream()
                                .map(LongValue::new)
                                .collect(Collectors.toList())
                );
                return new InExpression(new Column(dataProperties.getDeptIdColumn()), expressionList);

            case SELF_ONLY:
                // create_by = 当前用户ID
                return new EqualsTo(
                        new Column(dataProperties.getCreateByColumn()),
                        new StringValue(dataScopeInfo.getUserId().toString())
                );

            case CUSTOM:
                // dept_id IN (角色指定的部门列表)
                List<Long> customDeptIds = dataScopeInfo.getCustomDeptIds();
                if (customDeptIds == null || customDeptIds.isEmpty()) {
                    // 没有指定部门，只能看自己的
                    return new EqualsTo(
                            new Column(dataProperties.getCreateByColumn()),
                            new StringValue(dataScopeInfo.getUserId().toString())
                    );
                }
                ExpressionList customList = new ExpressionList();
                customList.setExpressions(
                        customDeptIds.stream()
                                .map(LongValue::new)
                                .collect(Collectors.toList())
                );
                return new InExpression(new Column(dataProperties.getDeptIdColumn()), customList);

            default:
                return null;
        }
    }

    /**
     * 为SELECT语句添加数据权限过滤
     *
     * @param select 查询语句
     * @param filter 过滤条件
     */
    private void addDataScopeToSelect(Select select, Expression filter) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();

        if (where == null) {
            plainSelect.setWhere(filter);
        } else {
            AndExpression andExpression = new AndExpression(where, filter);
            plainSelect.setWhere(andExpression);
        }
    }

    /**
     * 为UPDATE语句添加数据权限过滤
     *
     * @param update 更新语句
     * @param filter 过滤条件
     */
    private void addDataScopeToUpdate(Update update, Expression filter) {
        Expression where = update.getWhere();

        if (where == null) {
            update.setWhere(filter);
        } else {
            AndExpression andExpression = new AndExpression(where, filter);
            update.setWhere(andExpression);
        }
    }

    /**
     * 为DELETE语句添加数据权限过滤
     *
     * @param delete 删除语句
     * @param filter 过滤条件
     */
    private void addDataScopeToDelete(Delete delete, Expression filter) {
        Expression where = delete.getWhere();

        if (where == null) {
            delete.setWhere(filter);
        } else {
            AndExpression andExpression = new AndExpression(where, filter);
            delete.setWhere(andExpression);
        }
    }

    /**
     * 替换SQL
     *
     * @param boundSql 原BoundSql
     * @param newSql   新SQL
     */
    private void replaceSql(BoundSql boundSql, String newSql) {
        try {
            Field field = boundSql.getClass().getDeclaredField("sql");
            field.setAccessible(true);
            field.set(boundSql, newSql);
        } catch (Exception e) {
            log.error("Failed to replace SQL", e);
            throw new RuntimeException("SQL替换失败", e);
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
