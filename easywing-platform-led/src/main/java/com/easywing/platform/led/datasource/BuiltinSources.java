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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内建数据源登记处。
 *
 * <p>类加载时把 counter / now / static / script 注册进 {@link DataSourceType}。
 * jdbc 与 http 由各自模块在可用时注册（避免硬依赖驱动）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class BuiltinSources {

    private BuiltinSources() {}

    public static void registerAll() {
        DataSourceType.register("counter", (id, spec) -> new CounterSource(id, spec.params()));
        DataSourceType.register("now", (id, spec) -> new NowSource(id, spec.params()));
        DataSourceType.register("static", (id, spec) -> new StaticSource(id, spec.params()));
        DataSourceType.register("script", (id, spec) -> new ScriptSource(id, spec.params()));
    }

    // ---------------- counter ----------------

    /** 本地递增计数器：演示与无真实数据源时联调用。 */
    public static final class CounterSource implements DataSource {
        private final String id;
        private final double start;
        private final double step;
        private final int decimals;
        private final AtomicLong tick = new AtomicLong();

        public CounterSource(String id, Map<String, Object> p) {
            this.id = id;
            this.start = num(p, "start", 0);
            this.step = num(p, "step", 1);
            this.decimals = (int) num(p, "decimals", 0);
        }

        @Override
        public String type() {
            return "counter";
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public DataRow.Result fetch(FetchContext ctx) {
            long t = tick.incrementAndGet();
            double v = start + step * (t - 1);
            String text = decimals <= 0
                    ? String.valueOf((long) v)
                    : String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
            DataRow.Result r = new DataRow.Result(id);
            r.add(new DataRow().put("value", text).put("raw", v).put("tick", t));
            return r;
        }
    }

    // ---------------- now ----------------

    /** 当前时间源，支持 {@code pattern} 格式。 */
    public static final class NowSource implements DataSource {
        private final String id;
        private final String pattern;

        public NowSource(String id, Map<String, Object> p) {
            this.id = id;
            this.pattern = str(p, "pattern", "yyyy-MM-dd HH:mm:ss");
        }

        @Override
        public String type() {
            return "now";
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public DataRow.Result fetch(FetchContext ctx) {
            String text = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(pattern));
            DataRow.Result r = new DataRow.Result(id);
            r.add(new DataRow()
                    .put("value", text)
                    .put("date", java.time.LocalDate.now().toString())
                    .put("time", java.time.LocalTime.now().withNano(0).toString())
                    .put("timestamp", System.currentTimeMillis()));
            return r;
        }
    }

    // ---------------- static ----------------

    /** 固定文本源：配置即内容。 */
    public static final class StaticSource implements DataSource {
        private final String id;
        private final String text;

        public StaticSource(String id, Map<String, Object> p) {
            this.id = id;
            this.text = str(p, "text", "");
        }

        @Override
        public String type() {
            return "static";
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public DataRow.Result fetch(FetchContext ctx) {
            DataRow.Result r = new DataRow.Result(id);
            r.add(new DataRow().put("value", text).put("text", text));
            return r;
        }
    }

    // ---------------- script ----------------

    /**
     * JavaScript 表达式源（javax.script），可访问 now/counter 变量。
     *
     * <p><b>JDK 21 下不可用</b>：Nashorn 自 JDK 15 起移除，{@code getEngineByName("js")} 返回 null，
     * 故构造时即抛 {@link DataSourceException}，运行期 {@code counter}/{@code now}/{@code static}
     * 三个内建源不受影响。如确需脚本能力，请改用 GraalJS 或回退 JDK 11。
     */
    public static final class ScriptSource implements DataSource {
        private final String id;
        private final String script;
        private final javax.script.ScriptEngine engine;

        public ScriptSource(String id, Map<String, Object> p) {
            this.id = id;
            this.script = str(p, "script", "");
            this.engine = resolveEngine();
            if (this.engine == null) {
                throw new DataSourceException("script", id, "当前 JDK 未提供 JavaScript 引擎（JDK 15+ 已移除 Nashorn）");
            }
        }

        @Override
        public String type() {
            return "script";
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public DataRow.Result fetch(FetchContext ctx) {
            try {
                String js = script;
                if (isFilePath(js)) {
                    js = java.nio.file.Files.readString(
                            java.nio.file.Path.of(js), java.nio.charset.StandardCharsets.UTF_8);
                }
                javax.script.SimpleBindings b = new javax.script.SimpleBindings();
                b.put("now", java.time.LocalDateTime.now());
                b.put("counter", System.currentTimeMillis());
                Object out = engine.eval(js, b);
                DataRow.Result r = new DataRow.Result(id);
                r.add(new DataRow().put("value", out == null ? "" : out.toString()));
                return r;
            } catch (Exception e) {
                throw new DataSourceException("script", id, "脚本执行失败: " + e.getMessage(), e);
            }
        }

        private static javax.script.ScriptEngine resolveEngine() {
            return new javax.script.ScriptEngineManager().getEngineByName("js");
        }
    }

    // ---------------- helpers ----------------

    private static double num(Map<String, Object> p, String key, double def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(v.toString().trim());
    }

    private static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        return v == null ? def : v.toString();
    }

    /**
     * 判定脚本配置值是否为「文件路径」。
     */
    public static boolean isFilePath(String s) {
        if (s == null || s.isBlank() || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return false;
        }
        try {
            return java.nio.file.Files.exists(java.nio.file.Path.of(s));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
