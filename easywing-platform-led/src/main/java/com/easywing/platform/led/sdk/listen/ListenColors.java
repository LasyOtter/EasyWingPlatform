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

import java.util.Locale;

/**
 * V2014 颜色换算。
 *
 * <p>V2014 各区域对字体颜色的约定不统一：
 * <ul>
 *   <li>文本类：RGB 值，红 255、绿 65535、黄 65280；</li>
 *   <li>时钟/计时/内码区：1 红、2 绿、3 黄；</li>
 *   <li>边框：颜色值。</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class ListenColors {

    private ListenColors() {}

    /** RGB 值：红 255、绿 65535、黄 65280、蓝 16711680、青 16776960、紫 16711935、白 16777215。 */
    public static int rgb(String nameOrHex) {
        String s = normalize(nameOrHex);
        if (s == null) {
            return 255;
        }
        switch (s) {
            case "red":
                return 255;
            case "green":
                return 65535;
            case "yellow":
                return 65280;
            case "blue":
                return 16711680;
            case "cyan":
                return 16776960;
            case "purple":
                return 16711935;
            case "white":
                return 16777215;
            case "light":
                return 16777215;
            default:
                return parseHex(s, 255);
        }
    }

    /** 时钟/计时/内码区用色：1 红、2 绿、3 黄。 */
    public static int simple(String nameOrHex) {
        String s = normalize(nameOrHex);
        if (s == null) {
            return 1;
        }
        switch (s) {
            case "green":
                return 2;
            case "yellow":
                return 3;
            case "red":
                return 1;
            default:
                return 1;
        }
    }

    private static String normalize(String nameOrHex) {
        if (nameOrHex == null || nameOrHex.isBlank()) {
            return null;
        }
        String s = nameOrHex.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        return s;
    }

    private static int parseHex(String s, int def) {
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
