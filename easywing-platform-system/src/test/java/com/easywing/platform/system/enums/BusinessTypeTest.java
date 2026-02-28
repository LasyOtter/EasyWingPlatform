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
 * BusinessType 枚举单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class BusinessTypeTest {

    @Test
    @DisplayName("验证所有业务类型")
    void verifyAllBusinessTypes() {
        assertThat(BusinessType.INSERT.getCode()).isEqualTo(1);
        assertThat(BusinessType.INSERT.getDescription()).isEqualTo("新增");

        assertThat(BusinessType.UPDATE.getCode()).isEqualTo(2);
        assertThat(BusinessType.UPDATE.getDescription()).isEqualTo("修改");

        assertThat(BusinessType.DELETE.getCode()).isEqualTo(3);
        assertThat(BusinessType.DELETE.getDescription()).isEqualTo("删除");

        assertThat(BusinessType.EXPORT.getCode()).isEqualTo(4);
        assertThat(BusinessType.EXPORT.getDescription()).isEqualTo("导出");

        assertThat(BusinessType.IMPORT.getCode()).isEqualTo(5);
        assertThat(BusinessType.IMPORT.getDescription()).isEqualTo("导入");

        assertThat(BusinessType.LOGIN.getCode()).isEqualTo(6);
        assertThat(BusinessType.LOGIN.getDescription()).isEqualTo("登录");

        assertThat(BusinessType.LOGOUT.getCode()).isEqualTo(7);
        assertThat(BusinessType.LOGOUT.getDescription()).isEqualTo("登出");

        assertThat(BusinessType.OTHER.getCode()).isEqualTo(0);
        assertThat(BusinessType.OTHER.getDescription()).isEqualTo("其他");
    }

    @Test
    @DisplayName("验证枚举值数量")
    void verifyEnumCount() {
        BusinessType[] values = BusinessType.values();
        assertThat(values).hasSize(8);
    }
}
