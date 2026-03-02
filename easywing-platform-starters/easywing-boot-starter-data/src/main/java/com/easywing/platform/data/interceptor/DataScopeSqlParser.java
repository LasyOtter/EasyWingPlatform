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

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.easywing.platform.data.interceptor.DataScopeInterceptor.DataScopeInfo;
import com.easywing.platform.data.interceptor.DataScopeInterceptor.DataScopeType;
import com.easywing.platform.data.properties.DataProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
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
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据权限SQL解析器。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class DataScopeSqlParser {

    private static final String PARAMETER_PREFIX = "__dataScopeParam";
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    private final DataProperties dataProperties;

    public DataScopeSqlParser(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
    }

    /**
     * 应用数据权限过滤条件。
     *
     * @param boundSql       原始BoundSql
     * @param mappedStatement 映射语句
     * @param dataScopeInfo  数据权限信息
     * @return 是否已应用数据权限过滤
     */
    public boolean applyDataScope(BoundSql boundSql, MappedStatement mappedStatement, DataScopeInfo dataScopeInfo) {
        DataScopeFilter dataScopeFilter = buildDataScopeFilter(dataScopeInfo);
        if (dataScopeFilter == null) {
            return false;
        }

        Statement statement = parseStatement(boundSql.getSql());
        if (!applyDataScopeFilter(statement, dataScopeFilter.filter())) {
            return false;
        }

        applyParameters(boundSql, mappedStatement, dataScopeFilter.parameters());
        PluginUtils.mpBoundSql(boundSql).sql(statement.toString());
        return true;
    }

    private Statement parseStatement(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception ex) {
            throw new DataScopeSqlException("Failed to parse SQL for data scope", ex);
        }
    }

    private DataScopeFilter buildDataScopeFilter(DataScopeInfo dataScopeInfo) {
        DataScopeType dataScope = dataScopeInfo.getDataScope();
        if (dataScope == null) {
            return null;
        }

        return switch (dataScope) {
            case DEPT_ONLY -> buildDeptOnlyFilter(dataScopeInfo.getDeptId());
            case DEPT_AND_CHILD -> buildDeptAndChildFilter(dataScopeInfo.getDeptId(), dataScopeInfo.getChildDeptIds());
            case SELF_ONLY -> buildSelfOnlyFilter(dataScopeInfo.getUserId());
            case CUSTOM -> buildCustomFilter(dataScopeInfo.getUserId(), dataScopeInfo.getCustomDeptIds());
            case ALL -> null;
        };
    }

    private DataScopeFilter buildDeptOnlyFilter(Long deptId) {
        if (deptId == null) {
            return null;
        }
        return buildEqualsFilter(getDeptIdColumn(), deptId);
    }

    private DataScopeFilter buildDeptAndChildFilter(Long deptId, Set<Long> childDeptIds) {
        List<Long> deptIds = new ArrayList<>();
        if (childDeptIds != null && !childDeptIds.isEmpty()) {
            deptIds.addAll(childDeptIds);
        } else if (deptId != null) {
            deptIds.add(deptId);
        }
        if (deptIds.isEmpty()) {
            return null;
        }
        List<Long> sortedDeptIds = deptIds.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        return buildInFilter(getDeptIdColumn(), sortedDeptIds);
    }

    private DataScopeFilter buildSelfOnlyFilter(Long userId) {
        if (userId == null) {
            return null;
        }
        return buildEqualsFilter(getCreateByColumn(), userId);
    }

    private DataScopeFilter buildCustomFilter(Long userId, List<Long> customDeptIds) {
        if (customDeptIds == null || customDeptIds.isEmpty()) {
            return buildSelfOnlyFilter(userId);
        }
        return buildInFilter(getDeptIdColumn(), customDeptIds);
    }

    private DataScopeFilter buildEqualsFilter(Column column, Object value) {
        EqualsTo equalsTo = new EqualsTo(column, new JdbcParameter());
        return new DataScopeFilter(equalsTo, List.of(value));
    }

    private DataScopeFilter buildInFilter(Column column, List<Long> values) {
        ExpressionList expressionList = new ExpressionList();
        expressionList.setExpressions(values.stream()
                .map(value -> new JdbcParameter())
                .collect(Collectors.toList()));
        return new DataScopeFilter(new InExpression(column, expressionList), new ArrayList<>(values));
    }

    private Column getDeptIdColumn() {
        String columnName = dataProperties.getDeptIdColumn();
        validateColumnName(columnName);
        return new Column(columnName);
    }

    private Column getCreateByColumn() {
        String columnName = dataProperties.getCreateByColumn();
        validateColumnName(columnName);
        return new Column(columnName);
    }

    private void validateColumnName(String columnName) {
        if (columnName == null || !COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new DataScopeSqlException("Invalid data scope column name: " + columnName);
        }
    }

    private boolean applyDataScopeFilter(Statement statement, Expression filter) {
        if (statement instanceof Select select) {
            addDataScopeToSelect(select, filter);
            return true;
        }
        if (statement instanceof Update update) {
            addDataScopeToUpdate(update, filter);
            return true;
        }
        if (statement instanceof Delete delete) {
            addDataScopeToDelete(delete, filter);
            return true;
        }
        return false;
    }

    private void addDataScopeToSelect(Select select, Expression filter) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();

        if (where == null) {
            plainSelect.setWhere(filter);
        } else {
            plainSelect.setWhere(new AndExpression(where, filter));
        }
    }

    private void addDataScopeToUpdate(Update update, Expression filter) {
        Expression where = update.getWhere();

        if (where == null) {
            update.setWhere(filter);
        } else {
            update.setWhere(new AndExpression(where, filter));
        }
    }

    private void addDataScopeToDelete(Delete delete, Expression filter) {
        Expression where = delete.getWhere();

        if (where == null) {
            delete.setWhere(filter);
        } else {
            delete.setWhere(new AndExpression(where, filter));
        }
    }

    private void applyParameters(BoundSql boundSql, MappedStatement mappedStatement, List<Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        int startIndex = parameterMappings.size();

        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            String parameterName = PARAMETER_PREFIX + (startIndex + index);
            ParameterMapping mapping = new ParameterMapping.Builder(mappedStatement.getConfiguration(),
                    parameterName, value.getClass()).build();
            parameterMappings.add(mapping);
            boundSql.setAdditionalParameter(parameterName, value);
        }
    }

    private record DataScopeFilter(Expression filter, List<Object> parameters) {
    }
}
