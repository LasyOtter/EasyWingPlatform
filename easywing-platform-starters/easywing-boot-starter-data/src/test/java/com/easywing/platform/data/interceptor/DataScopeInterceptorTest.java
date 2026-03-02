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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.easywing.platform.data.datascope.DataScopeContext;
import com.easywing.platform.data.properties.DataProperties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * DataScopeInterceptor 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DataScopeInterceptorTest {

    @Mock
    private DataProperties dataProperties;

    @Mock
    private DataScopeInterceptor.DataScopeHandler dataScopeHandler;

    @Mock
    private Executor executor;

    @Mock
    private MappedStatement mappedStatement;

    @Mock
    private ResultHandler resultHandler;

    private DataScopeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        when(dataProperties.getDeptIdColumn()).thenReturn("dept_id");
        when(dataProperties.getCreateByColumn()).thenReturn("create_by");
        interceptor = new DataScopeInterceptor(dataProperties, dataScopeHandler);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("忽略数据权限-直接返回")
    void beforeQuery_IgnoreDataScope() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeContext.setIgnoreDataScope(true);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        assertThat(getSql(boundSql)).isEqualTo(originalSql);
    }

    @Test
    @DisplayName("无数据权限信息-直接返回")
    void beforeQuery_NoDataScopeInfo() throws NoSuchFieldException, IllegalAccessException {
        // Given
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(null);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        assertThat(getSql(boundSql)).isEqualTo(originalSql);
    }

    @Test
    @DisplayName("超级管理员-跳过数据权限")
    void beforeQuery_AdminUser() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo adminInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.ALL, true, null, null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(adminInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        assertThat(getSql(boundSql)).isEqualTo(originalSql);
    }

    @Test
    @DisplayName("全部数据权限-不需要过滤")
    void beforeQuery_AllDataScope() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo allScopeInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.ALL, false, null, null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(allScopeInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        assertThat(getSql(boundSql)).isEqualTo(originalSql);
    }

    @Test
    @DisplayName("仅本人权限-添加create_by过滤")
    void beforeQuery_SelfOnly() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo selfOnlyInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.SELF_ONLY, false, null, null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(selfOnlyInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("create_by = '1'");
    }

    @Test
    @DisplayName("本部门权限-添加dept_id过滤")
    void beforeQuery_DeptOnly() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo deptOnlyInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.DEPT_ONLY, false, null, null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(deptOnlyInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("dept_id = 100");
    }

    @Test
    @DisplayName("本部门及以下权限-添加dept_id IN过滤")
    void beforeQuery_DeptAndChild() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo deptAndChildInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.DEPT_AND_CHILD, false,
                Set.of(101L, 102L), null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(deptAndChildInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("dept_id IN (");
    }

    @Test
    @DisplayName("自定义权限-添加dept_id IN过滤")
    void beforeQuery_Custom() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo customInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.CUSTOM, false,
                null, List.of(201L, 202L));
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(customInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("dept_id IN (");
    }

    @Test
    @DisplayName("自定义权限无部门-降级为仅本人")
    void beforeQuery_CustomNoDepts_FallbackToSelfOnly() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo customInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.CUSTOM, false,
                null, List.of());
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(customInfo);

        String originalSql = "SELECT * FROM sys_user WHERE status = 0";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("create_by = '1'");
    }

    @Test
    @DisplayName("无WHERE子句的SQL-添加WHERE条件")
    void beforeQuery_NoWhereClause() throws NoSuchFieldException, IllegalAccessException {
        // Given
        DataScopeInterceptor.DataScopeInfo selfOnlyInfo = createDataScopeInfo(
                1L, 100L, DataScopeInterceptor.DataScopeType.SELF_ONLY, false, null, null);
        when(dataScopeHandler.getDataScopeInfo()).thenReturn(selfOnlyInfo);

        String originalSql = "SELECT * FROM sys_user";
        BoundSql boundSql = createBoundSql(originalSql);

        // When
        interceptor.beforeQuery(executor, mappedStatement, null, RowBounds.DEFAULT, resultHandler, boundSql);

        // Then
        String modifiedSql = getSql(boundSql);
        assertThat(modifiedSql).contains("WHERE");
        assertThat(modifiedSql).contains("create_by = '1'");
    }

    private BoundSql createBoundSql(String sql) throws NoSuchFieldException, IllegalAccessException {
        BoundSql boundSql = mock(BoundSql.class);
        Field sqlField = BoundSql.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        // 使用反射设置sql字段
        when(boundSql.getSql()).thenReturn(sql);
        return boundSql;
    }

    private String getSql(BoundSql boundSql) throws NoSuchFieldException, IllegalAccessException {
        Field sqlField = BoundSql.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        return (String) sqlField.get(boundSql);
    }

    private DataScopeInterceptor.DataScopeInfo createDataScopeInfo(
            Long userId, Long deptId, DataScopeInterceptor.DataScopeType dataScope,
            boolean isAdmin, Set<Long> childDeptIds, List<Long> customDeptIds) {
        return new DataScopeInterceptor.DataScopeInfo() {
            @Override
            public Long getUserId() { return userId; }
            @Override
            public Long getDeptId() { return deptId; }
            @Override
            public DataScopeInterceptor.DataScopeType getDataScope() { return dataScope; }
            @Override
            public boolean isAdmin() { return isAdmin; }
            @Override
            public Set<Long> getChildDeptIds() { return childDeptIds; }
            @Override
            public List<Long> getCustomDeptIds() { return customDeptIds; }
        };
    }
}
