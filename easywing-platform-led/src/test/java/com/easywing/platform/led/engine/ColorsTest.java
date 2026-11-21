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
package com.easywing.platform.led.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link Colors} 单元测试（BBGGRR 与 %disp 颜色符号）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ColorsTest {

    @Test
    void resolvesNamedColorsAsBbggrr() {
        assertEquals(0x0000ff, Colors.of("red"));
        assertEquals(0x00ff00, Colors.of("green"));
        assertEquals(0xff0000, Colors.of("blue"));
        assertEquals(0x00ffff, Colors.of("yellow"));
    }

    @Test
    void resolvesHexWithOrWithoutPrefix() {
        assertEquals(0x0000ff, Colors.of("0xff"));
        assertEquals(0x0000ff, Colors.of("#ff"));
    }

    @Test
    void neimaColorSymbols() {
        assertEquals(':', Colors.neimaColorSymbol("red"));
        assertEquals(';', Colors.neimaColorSymbol("green"));
        assertEquals('<', Colors.neimaColorSymbol("yellow"));
        assertEquals('=', Colors.neimaColorSymbol("purple"));
        assertEquals('>', Colors.neimaColorSymbol("blue"));
        assertEquals('@', Colors.neimaColorSymbol("white"));
        assertEquals(';', Colors.neimaColorSymbol(null));
    }
}
