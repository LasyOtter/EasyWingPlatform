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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 数据源管理器：按 id 缓存数据源实例，并做失败限流（避免每轮都打挂掉的源）。
 *
 * <p>生命周期由 {@code LedRefreshService} 在启动/停止时驱动。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class DataSourceManager {

    /** 连续失败达到该次数后进入冷却，不再立即重试。 */
    private static final int FAILURE_THRESHOLD = 3;
    /** 冷却时长（毫秒）。 */
    private static final long COOL_DOWN_MILLIS = 60_000L;

    private final Map<String, DataSource> sources = new LinkedHashMap<>();
    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> coolDownUntil = new ConcurrentHashMap<>();

    /** 注册一个数据源实例（按 id 缓存）。 */
    public synchronized void register(DataSource ds) {
        if (ds == null) {
            return;
        }
        DataSource old = sources.put(ds.id(), ds);
        if (old != null) {
            old.close();
        }
    }

    /** 按 id 取数据源。 */
    public DataSource get(String id) {
        return sources.get(id);
    }

    /** 已注册的数据源 id 列表。 */
    public synchronized List<String> ids() {
        return new ArrayList<>(sources.keySet());
    }

    /** 取数：带失败计数与冷却。失败抛出 {@link DataSourceException}。 */
    public DataRow.Result fetch(String id, FetchContext ctx) {
        DataSource ds = sources.get(id);
        if (ds == null) {
            throw new DataSourceException("unknown", id, "数据源未配置: " + id);
        }
        Long until = coolDownUntil.get(id);
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                throw new DataSourceException(ds.type(), id, "数据源冷却中，暂缓取数");
            }
            coolDownUntil.remove(id);
        }
        try {
            DataRow.Result r = ds.fetch(ctx);
            failures.remove(id);
            return r;
        } catch (RuntimeException e) {
            int[] f = failures.computeIfAbsent(id, k -> new int[1]);
            f[0]++;
            if (f[0] >= FAILURE_THRESHOLD) {
                coolDownUntil.put(id, System.currentTimeMillis() + COOL_DOWN_MILLIS);
                failures.remove(id);
            }
            throw e;
        }
    }

    /** 当前处于冷却的数据源。 */
    public List<String> coolingDown() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : coolDownUntil.entrySet()) {
            if (e.getValue() > now) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 释放全部数据源。 */
    public synchronized void closeAll() {
        for (DataSource ds : sources.values()) {
            try {
                ds.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
        sources.clear();
        failures.clear();
        coolDownUntil.clear();
    }
}
