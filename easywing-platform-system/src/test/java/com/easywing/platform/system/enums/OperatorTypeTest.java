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
 * OperatorType 枚举单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class OperatorTypeTest {

    @Test
    @DisplayName("验证所有操作者类型")
    void verifyAllOperatorTypes() {
        assertThat(OperatorType.MANAGE.getCode()).isEqualTo(1);
        assertThat(OperatorType.MANAGE.getDescription()).isEqualTo("后台用户");

        assertThat(OperatorType.MOBILE.getCode()).isEqualTo(2);
        assertThat(OperatorType.MOBILE.getDescription()).isEqualTo("手机端用户");

        assertThat(OperatorType.OTHER.getCode()).isEqualTo(0);
        assertThat(OperatorType.OTHER.getDescription()).isEqualTo("其他");
    }

    @Test
    @DisplayName("验证枚举值数量")
    void verifyEnumCount() {
        OperatorType[] values = OperatorType.values();
        assertThat(values).hasSize(3);
    }
}
