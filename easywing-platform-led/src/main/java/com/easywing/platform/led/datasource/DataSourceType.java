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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 数据源类型注册表：类型名 → 工厂。
 *
 * <p>配置里 {@code type: jdbc} 即查此表。内建类型在 {@link BuiltinSources} 中登记，
 * 业务方可通过 {@link #register(String, Factory)} 扩展自己的类型。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class DataSourceType {

    private static final Map<String, DataSource.Factory> FACTORIES = new ConcurrentHashMap<>();
    private static final List<String> ORDER = new CopyOnWriteArrayList<>();

    /** 运行环境是否提供 {@code js} 脚本引擎（JDK 15+ 为 false，Nashorn 已移除）。 */
    private static final boolean ENGINE_PRESENT = new javax.script.ScriptEngineManager().getEngineByName("js") != null;

    private DataSourceType() {}

    /** 注册（或覆盖）一个数据源类型。 */
    public static void register(String type, DataSource.Factory factory) {
        String key = normalize(type);
        if (key == null) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        FACTORIES.put(key, factory);
        ORDER.remove(key);
        ORDER.add(key);
    }

    /** 按类型名创建数据源。 */
    public static DataSource create(DataSource.DataSourceSpec spec) {
        String key = normalize(spec.type());
        DataSource.Factory f = key == null ? null : FACTORIES.get(key);
        if (f == null) {
            throw new DataSourceException(
                    spec.type(), spec.id(), "未注册的数据源类型: " + spec.type() + "，可用类型: " + available());
        }
        if ("script".equals(key) && !ENGINE_PRESENT) {
            throw new DataSourceException(
                    spec.type(),
                    spec.id(),
                    "当前 JDK 未提供 JavaScript 引擎" + "（JDK 15+ 已移除 Nashorn），script 数据源不可用；请改用 GraalJS 或回退 JDK 11");
        }
        return f.create(spec.id(), spec);
    }

    /** 该类型是否已注册。 */
    public static boolean supports(String type) {
        String key = normalize(type);
        return key != null && FACTORIES.containsKey(key);
    }

    /** 已注册类型列表（按注册顺序）。 */
    public static List<String> available() {
        return List.copyOf(ORDER);
    }

    private static String normalize(String type) {
        if (type == null) {
            return null;
        }
        String s = type.trim().toLowerCase(java.util.Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** 创建复合参数映射的小工具。 */
    public static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** 便捷函数式工厂。 */
    public static DataSource.Factory fn(Function<DataSource.DataSourceSpec, DataSource> f) {
        return (id, spec) -> f.apply(spec);
    }
}
