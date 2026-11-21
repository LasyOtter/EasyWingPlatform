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

import com.listenvision.led;
import java.util.Locale;

/**
 * 颜色名 / 十六进制字符串 → SDK 的 BBGGRR 整数值。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class Colors {

    private Colors() {}

    public static int of(String nameOrHex) {
        if (nameOrHex == null || nameOrHex.isBlank()) {
            return led.COLOR_RED;
        }
        String s = nameOrHex.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "red":
                return led.COLOR_RED; // 0x0000ff
            case "green":
                return led.COLOR_GREEN; // 0x00ff00
            case "blue":
                return led.COLOR_BLUE; // 0xff0000
            case "yellow":
                return led.COLOR_YELLOW; // 0x00ffff
            case "cyan":
                return led.COLOR_CYAN; // 0xffff00
            case "purple":
                return led.COLOR_PURPLE; // 0xff00ff
            case "white":
                return led.COLOR_WHITE; // 0xffffff
            case "light":
                return led.COLOR_WHITE; // 淡色按白处理
            default:
                if (s.startsWith("#")) {
                    s = s.substring(1);
                }
                if (s.startsWith("0x")) {
                    s = s.substring(2);
                }
                // 支持 RRGGBB 或 BBGGRR；配置里统一按 BBGGRR 解析
                return (int) Long.parseLong(s, 16);
        }
    }

    /** 内码刷新协议 %disp 的颜色符号。 */
    public static char neimaColorSymbol(String colorName) {
        if (colorName == null) {
            return ';';
        }
        switch (colorName.trim().toLowerCase(Locale.ROOT)) {
            case "red":
                return ':';
            case "green":
                return ';';
            case "yellow":
                return '<';
            case "purple":
                return '=';
            case "blue":
                return '>';
            case "light":
                return '?';
            case "white":
                return '@';
            default:
                return ';';
        }
    }
}
