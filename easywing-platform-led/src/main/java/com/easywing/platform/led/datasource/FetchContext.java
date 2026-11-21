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

/**
 * 一次取数的上下文。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class FetchContext {

    private final java.util.Map<String, Object> params;

    public FetchContext(java.util.Map<String, Object> params) {
        this.params = params == null ? java.util.Map.of() : params;
    }

    public static FetchContext of(java.util.Map<String, Object> params) {
        return new FetchContext(params);
    }

    public String str(String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    public String str(String key, String def) {
        String v = str(key);
        return v == null || v.isBlank() ? def : v;
    }

    public int integer(String key, int def) {
        Object v = params.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString().trim());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) params.get(key);
    }

    public boolean has(String key) {
        return params.containsKey(key);
    }
}
