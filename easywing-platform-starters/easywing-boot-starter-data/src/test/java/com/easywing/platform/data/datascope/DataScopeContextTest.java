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
package com.easywing.platform.data.datascope;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DataScopeContext 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("默认不忽略数据权限")
    void default_NotIgnoreDataScope() {
        assertThat(DataScopeContext.isIgnoreDataScope()).isFalse();
    }

    @Test
    @DisplayName("设置忽略数据权限")
    void setIgnoreDataScope() {
        DataScopeContext.setIgnoreDataScope(true);
        assertThat(DataScopeContext.isIgnoreDataScope()).isTrue();
    }

    @Test
    @DisplayName("清除后恢复默认状态")
    void clear_RestoreDefault() {
        DataScopeContext.setIgnoreDataScope(true);
        assertThat(DataScopeContext.isIgnoreDataScope()).isTrue();

        DataScopeContext.clear();
        assertThat(DataScopeContext.isIgnoreDataScope()).isFalse();
    }

    @Test
    @DisplayName("多次设置和清除")
    void multipleSetAndClear() {
        DataScopeContext.setIgnoreDataScope(true);
        assertThat(DataScopeContext.isIgnoreDataScope()).isTrue();

        DataScopeContext.setIgnoreDataScope(false);
        assertThat(DataScopeContext.isIgnoreDataScope()).isFalse();

        DataScopeContext.setIgnoreDataScope(true);
        assertThat(DataScopeContext.isIgnoreDataScope()).isTrue();

        DataScopeContext.clear();
        assertThat(DataScopeContext.isIgnoreDataScope()).isFalse();
    }
}
