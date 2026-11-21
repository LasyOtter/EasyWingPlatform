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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.easywing.platform.led.config.RefreshConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ProgramBuilder} 纯逻辑单元测试（内码帧拼装）。
 *
 * <p>契约：{@code RefreshContext#renderText} 返回「已套好模板的最终文本」，
 * {@code neimaFrame} 只负责拼协议头（区域号 + 颜色符号 + 特效）与该文本。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ProgramBuilderTest {

    private static RefreshConfig.Binding binding(int areaIndex, String color, int effect) {
        RefreshConfig.Binding b = new RefreshConfig.Binding();
        b.setAreaIndex(areaIndex);
        b.setColor(color);
        b.setEffect(effect);
        return b;
    }

    @Test
    void buildsSingleAreaFrame() {
        String frame = ProgramBuilder.neimaFrame(List.of(binding(0, "red", 0)), b -> "温度25℃");
        assertEquals("%disp0:0温度25℃", frame);
    }

    @Test
    void buildsMultiAreaFrameInOrder() {
        String frame = ProgramBuilder.neimaFrame(
                List.of(binding(0, "red", 0), binding(1, "green", 2)), b -> "v" + b.getAreaIndex());
        assertEquals("%disp0:0v0%disp1;2v1", frame);
    }

    @Test
    void skipsBindingsThatFailToRender() {
        String frame = ProgramBuilder.neimaFrame(List.of(binding(0, "red", 0), binding(1, "green", 0)), b -> {
            if (b.getAreaIndex() == 1) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        });
        assertEquals("%disp0:0ok", frame);
    }

    @Test
    void areaCharAndEffectDigitRanges() {
        assertEquals('0', ProgramBuilder.neimaAreaChar(0));
        assertEquals('9', ProgramBuilder.neimaAreaChar(9));
        assertEquals('O', ProgramBuilder.neimaAreaChar(31));
        assertThrows(IllegalArgumentException.class, () -> ProgramBuilder.neimaAreaChar(32));
        assertThrows(IllegalArgumentException.class, () -> ProgramBuilder.effectDigit(10));
    }
}
