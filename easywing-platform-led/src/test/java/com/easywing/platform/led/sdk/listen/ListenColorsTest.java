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
package com.easywing.platform.led.sdk.listen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link ListenColors} 单元测试（V2014 文本区 RGB 值 vs 时钟/内码 1/2/3）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ListenColorsTest {

    @Test
    void rgbValues() {
        assertEquals(255, ListenColors.rgb("red"));
        assertEquals(65535, ListenColors.rgb("green"));
        assertEquals(65280, ListenColors.rgb("yellow"));
        assertEquals(16777215, ListenColors.rgb("white"));
    }

    @Test
    void simpleValues() {
        assertEquals(1, ListenColors.simple("red"));
        assertEquals(2, ListenColors.simple("green"));
        assertEquals(3, ListenColors.simple("yellow"));
    }

    @Test
    void fallsBackToRedOnUnknown() {
        assertEquals(255, ListenColors.rgb("not-a-color"));
        assertEquals(1, ListenColors.simple("not-a-color"));
        assertEquals(255, ListenColors.rgb(null));
    }
}
