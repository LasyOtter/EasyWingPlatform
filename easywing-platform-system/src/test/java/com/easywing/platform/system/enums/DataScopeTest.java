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
package com.easywing.platform.system.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DataScope 枚举单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class DataScopeTest {

    @Test
    @DisplayName("验证所有数据权限范围")
    void verifyAllDataScopes() {
        // Verify each enum constant
        assertThat(DataScope.ALL.getCode()).isEqualTo(1);
        assertThat(DataScope.ALL.getDescription()).isEqualTo("全部数据权限");

        assertThat(DataScope.CUSTOM.getCode()).isEqualTo(2);
        assertThat(DataScope.CUSTOM.getDescription()).isEqualTo("自定义数据权限");

        assertThat(DataScope.DEPT_ONLY.getCode()).isEqualTo(3);
        assertThat(DataScope.DEPT_ONLY.getDescription()).isEqualTo("本部门数据权限");

        assertThat(DataScope.DEPT_AND_CHILD.getCode()).isEqualTo(4);
        assertThat(DataScope.DEPT_AND_CHILD.getDescription()).isEqualTo("本部门及以下数据权限");

        assertThat(DataScope.SELF_ONLY.getCode()).isEqualTo(5);
        assertThat(DataScope.SELF_ONLY.getDescription()).isEqualTo("仅本人数据权限");
    }

    @Test
    @DisplayName("验证枚举值数量")
    void verifyEnumCount() {
        DataScope[] values = DataScope.values();
        assertThat(values).hasSize(5);
    }

    @Test
    @DisplayName("根据code获取枚举")
    void getByCode() {
        // This tests the ordinal-based access used in the service
        assertThat(DataScope.values()[0]).isEqualTo(DataScope.ALL);
        assertThat(DataScope.values()[1]).isEqualTo(DataScope.CUSTOM);
        assertThat(DataScope.values()[2]).isEqualTo(DataScope.DEPT_ONLY);
        assertThat(DataScope.values()[3]).isEqualTo(DataScope.DEPT_AND_CHILD);
        assertThat(DataScope.values()[4]).isEqualTo(DataScope.SELF_ONLY);
    }
}
