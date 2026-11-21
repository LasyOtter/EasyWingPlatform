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
package com.easywing.platform.led.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link DurationParser} 单元测试。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class DurationParserTest {

    @Test
    void parsesSeconds() {
        assertEquals(30_000L, DurationParser.parseMillis("30s", 0));
    }

    @Test
    void parsesMinutes() {
        assertEquals(60_000L, DurationParser.parseMillis("1m", 0));
    }

    @Test
    void parsesMilliseconds() {
        assertEquals(500L, DurationParser.parseMillis("500ms", 0));
    }

    @Test
    void parsesHours() {
        assertEquals(3_600_000L, DurationParser.parseMillis("1h", 0));
    }

    @Test
    void fallsBackToDefaultOnBlankOrInvalid() {
        assertEquals(1_000L, DurationParser.parseMillis(null, 1_000L));
        assertEquals(1_000L, DurationParser.parseMillis("  ", 1_000L));
        assertEquals(1_000L, DurationParser.parseMillis("abc", 1_000L));
    }

    @Test
    void refreshConfigIntervalMillisUsesDefault() {
        RefreshConfig rc = new RefreshConfig();
        assertEquals(30_000L, rc.intervalMillis());
        rc.setInterval("2m");
        assertEquals(120_000L, rc.intervalMillis());
    }
}
