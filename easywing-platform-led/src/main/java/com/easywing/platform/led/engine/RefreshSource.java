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

import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.RefreshConfig;
import com.easywing.platform.led.datasource.BuiltinSources;
import com.easywing.platform.led.datasource.DataRow;
import com.easywing.platform.led.datasource.DataSource;
import com.easywing.platform.led.datasource.DataSourceException;
import com.easywing.platform.led.datasource.DataSourceManager;
import com.easywing.platform.led.datasource.DataSourceType;
import com.easywing.platform.led.datasource.FetchContext;
import com.easywing.platform.led.datasource.HttpDataSource;
import com.easywing.platform.led.datasource.JdbcDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 刷新数据源桥接：把 {@code refresh/*.yml} 的 binding 解析成实际文本。
 *
 * <p>两条路径：
 * <ol>
 *   <li>binding.source 命中 {@code led.yml} 的命名数据源 → 走 {@link DataSourceManager}，
 *       模板字段取自 SQL/HTTP 返回的行；</li>
 *   <li>否则按内建类型（counter/now/static/script/http 简写）现场解析。</li>
 * </ol>
 *
 * <p>本类同时承担命名数据源的创建与生命周期（{@link #close()}）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public final class RefreshSource implements ProgramBuilder.RefreshContext {

    private final LedConfig config;
    private final DataSourceManager manager = new DataSourceManager();
    /** 命名数据源的参数缓存（binding → 参数）。 */
    private final Map<RefreshConfig.Binding, Map<String, Object>> inlineParams = new LinkedHashMap<>();
    /** 内建 http 简写源实例缓存（binding → DataSource）：避免每帧新建 HttpClient。 */
    private final Map<RefreshConfig.Binding, DataSource> inlineHttpSources = new ConcurrentHashMap<>();
    /** counter 源自增计数。 */
    private final Map<String, Long> counterTick = new ConcurrentHashMap<>();

    public RefreshSource(LedConfig config) {
        this.config = config;
        DataSourceType.register("jdbc", (id, spec) -> new JdbcDataSource(id, spec.params()));
        DataSourceType.register("http", (id, spec) -> new HttpDataSource(id, spec.params()));
        BuiltinSources.registerAll();
        for (Map.Entry<String, LedConfig.SourceDef> e : config.getSources().entrySet()) {
            LedConfig.SourceDef def = e.getValue();
            DataSource.DataSourceSpec spec = new DataSource.DataSourceSpec(def.getType(), e.getKey(), def.getParams());
            try {
                manager.register(DataSourceType.create(spec));
            } catch (RuntimeException ex) {
                log.warn("[数据源] 命名数据源 {} 创建失败，已跳过: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    @Override
    public String renderText(RefreshConfig.Binding b) {
        DataSource ds = manager.get(b.getSource());
        if (ds != null) {
            DataRow.Result r = manager.fetch(b.getSource(), FetchContext.of(Map.of()));
            if (r.isEmpty()) {
                return "";
            }
            return r.rows().get(0).render(b.getTemplate());
        }
        return renderInline(b);
    }

    /** 内建简写源渲染。 */
    private String renderInline(RefreshConfig.Binding b) {
        String source = b.getSource() == null ? "counter" : b.getSource().toLowerCase(java.util.Locale.ROOT);
        return switch (source) {
            case "counter" -> counter(b);
            case "now" -> now(b);
            case "static" -> b.getTemplate() == null ? "" : b.getTemplate();
            case "script" -> script(b);
            case "http" -> httpInline(b);
            default -> throw new DataSourceException(
                    b.getSource(), null, "未知数据源: " + b.getSource() + "（非命名数据源，也不属内建类型）");
        };
    }

    private String counter(RefreshConfig.Binding b) {
        double start = b.getCounterStart() != null ? b.getCounterStart() : 0;
        double step = b.getCounterStep() != null ? b.getCounterStep() : 1;
        int decimals = b.getCounterDecimals() != null ? b.getCounterDecimals() : 0;
        long tick = counterTick.merge(bKey(b), 1L, Long::sum);
        double v = start + step * (tick - 1);
        String value = decimals <= 0
                ? String.valueOf((long) v)
                : String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
        return fill(value, b.getTemplate());
    }

    private String now(RefreshConfig.Binding b) {
        String pattern = b.getNowPattern() == null ? "yyyy-MM-dd HH:mm:ss" : b.getNowPattern();
        String value = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(pattern));
        return fill(value, b.getTemplate());
    }

    private String script(RefreshConfig.Binding b) {
        throw new DataSourceException("script", null, "当前 JDK 未提供 JavaScript 引擎（JDK 15+ 已移除 Nashorn），内建 script 源不可用");
    }

    private String httpInline(RefreshConfig.Binding b) {
        DataSource ds = inlineHttpSources.get(b);
        if (ds == null) {
            Map<String, Object> p = inlineParams.get(b);
            if (p == null) {
                p = new LinkedHashMap<>();
                p.put("url", b.getHttpUrl());
                p.put("field", b.getHttpField());
                p.put("method", "GET");
                inlineParams.put(b, p);
            }
            // 复用同一实例：HttpClient 非 AutoCloseable，每帧新建会持续累积线程/选择器
            ds = DataSourceType.create(new DataSource.DataSourceSpec("http", "inline-" + b.getAreaIndex(), p));
            inlineHttpSources.put(b, ds);
        }
        return ds.fetch(FetchContext.of(Map.of())).rows().stream()
                .findFirst()
                .map(r -> r.render(b.getTemplate()))
                .orElse("");
    }

    /** 把值填入模板；无模板时原样返回。 */
    private static String fill(String value, String template) {
        if (template == null || template.isBlank()) {
            return value;
        }
        return template.replace("{value}", value).replace("{text}", value);
    }

    private static String bKey(RefreshConfig.Binding b) {
        return b.getScreen() + "#" + b.getAreaIndex() + "@" + b.getTemplate();
    }

    /** 释放命名数据源连接。 */
    public void close() {
        manager.closeAll();
        for (DataSource ds : inlineHttpSources.values()) {
            try {
                ds.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
        inlineHttpSources.clear();
    }

    /** 供启动日志使用：列出已注册的命名数据源。 */
    public String describe() {
        return "命名数据源=" + manager.ids();
    }

    /** 直接取某个命名数据源的原始结果（供诊断接口使用）。 */
    public DataRow.Result renderSource(String id) {
        return manager.fetch(id, FetchContext.of(Map.of()));
    }
}
