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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 "30s" / "1m" / "500ms" / "2h" 这类时长字符串。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class DurationParser {

    private static final Pattern P = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h)?");

    private DurationParser() {}

    public static long parseMillis(String text, long defaultMillis) {
        if (text == null || text.trim().isEmpty()) {
            return defaultMillis;
        }
        Matcher m = P.matcher(text.trim().toLowerCase());
        if (!m.matches()) {
            return defaultMillis;
        }
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit == null || unit.equals("s")) {
            return (long) (v * 1000);
        } else if (unit.equals("ms")) {
            return (long) v;
        } else if (unit.equals("m")) {
            return (long) (v * 60_000);
        } else if (unit.equals("h")) {
            return (long) (v * 3_600_000);
        }
        return defaultMillis;
    }
}
