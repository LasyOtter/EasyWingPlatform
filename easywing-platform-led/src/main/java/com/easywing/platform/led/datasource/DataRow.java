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
package com.easywing.platform.led.datasource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 取数结果：一行记录（字段名 → 值）。
 *
 * <p>数据源返回行列表，刷新配置中的模板用 {@code {字段名}} 引用字段，
 * 未命中的占位符保留原样，便于排查配置问题。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class DataRow {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    public DataRow put(String name, Object value) {
        if (name != null) {
            fields.put(name, value);
        }
        return this;
    }

    public Object get(String name) {
        return fields.get(name);
    }

    /** 取字段并转字符串，缺失返回 null。 */
    public String str(String name) {
        Object v = fields.get(name);
        return v == null ? null : v.toString();
    }

    /** 取字段并转字符串，缺失返回空串。 */
    public String strOrEmpty(String name) {
        String v = str(name);
        return v == null ? "" : v;
    }

    public boolean has(String name) {
        return fields.containsKey(name);
    }

    public Map<String, Object> fields() {
        return fields;
    }

    /** 当前行渲染模板；{@code {字段}} 替换为字段值，未命中的占位保留。 */
    public String render(String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(template.length() + 16);
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String key = template.substring(i + 1, end).trim();
                    Object v = fields.get(key);
                    sb.append(v == null ? template.substring(i, end + 1) : v.toString());
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return fields.toString();
    }

    /**
     * 多行结果。
     */
    public static final class Result {
        private final List<DataRow> rows = new ArrayList<>();
        private final String sourceId;

        public Result(String sourceId) {
            this.sourceId = sourceId;
        }

        public Result add(DataRow row) {
            rows.add(row);
            return this;
        }

        public List<DataRow> rows() {
            return rows;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public int size() {
            return rows.size();
        }

        public String sourceId() {
            return sourceId;
        }
    }
}
