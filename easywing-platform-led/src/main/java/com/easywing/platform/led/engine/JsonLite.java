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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器：仅支持对象、数组、字符串、数字、布尔、null，
 * 用于 http 数据源取字段，避免引入额外依赖。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class JsonLite {

    private JsonLite() {}

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object v = p.value();
        p.skipWhitespace();
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("JSON 意外结束");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    return expect("true") ? Boolean.TRUE : null;
                case 'f':
                    return expect("false") ? Boolean.FALSE : null;
                case 'n':
                    return expect("null") ? null : null;
                default:
                    return number();
            }
        }

        private Object object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (i < s.length()) {
                skipWhitespace();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw new IllegalArgumentException("JSON 对象键必须是字符串 @" + i);
                }
                String key = string();
                skipWhitespace();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw new IllegalArgumentException("JSON 缺少冒号 @" + i);
                }
                i++;
                skipWhitespace();
                m.put(key, value());
                skipWhitespace();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                break;
            }
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
            }
            return m;
        }

        private Object array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (i < s.length()) {
                skipWhitespace();
                list.add(value());
                skipWhitespace();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                break;
            }
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
            }
            return list;
        }

        private String string() {
            if (s.charAt(i) != '"') {
                throw new IllegalArgumentException("JSON 字符串必须以 \" 开始 @" + i);
            }
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            }
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("JSON 字符串未闭合");
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty()) {
                throw new IllegalArgumentException("JSON 非法数字 @" + start);
            }
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.parseDouble(num);
            }
            return Long.parseLong(num);
        }

        private boolean expect(String word) {
            if (s.startsWith(word, i)) {
                i += word.length();
                return true;
            }
            throw new IllegalArgumentException("JSON 非法字面量 @" + i);
        }
    }
}
