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
package com.easywing.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link LdapFilter} 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class LdapFilterTest {

    @Test
    void encodeShouldEscapeRfc4515ReservedCharacters() {
        assertThat(LdapFilter.encode("a*b(c)d\\e")).isEqualTo("a\\2ab\\28c\\29d\\5ce");
    }

    @Test
    void encodeShouldKeepOrdinaryValuesUnchanged() {
        assertThat(LdapFilter.encode("john.doe@example.com")).isEqualTo("john.doe@example.com");
    }

    @Test
    void encodeShouldTreatNullAsEmpty() {
        assertThat(LdapFilter.encode(null)).isEmpty();
    }

    @Test
    void buildShouldReplacePlaceholderWithEscapedValue() {
        assertThat(LdapFilter.build("(uid={0})", "al*ice")).isEqualTo("(uid=al\\2aice)");
    }
}
