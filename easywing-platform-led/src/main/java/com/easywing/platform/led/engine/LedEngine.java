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
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.sdk.LedSdk;
import com.easywing.platform.led.sdk.SdkRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LED 引擎：下发编排 + 内码实时刷新调度。
 *
 * <p>与 {@link ScreenRegistry}（只读索引）不同，本类是运行期入口：
 * <ul>
 *   <li>{@link #deployAll} / {@link #deployScreen} 走各屏对应的 {@link LedSdk} 实现整屏下发；</li>
 *   <li>{@link #startRefresh} 为每个有刷新配置的屏启动 fixed-delay 定时任务，
 *       同一屏的多个 binding 合并为一帧 %disp 下发。</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class LedEngine {

    private final ScreenRegistry registry;
    private final LedConfig config;
    private final RefreshSource refreshSource;
    private final ObjectProvider<SdkRegistry> registryProvider;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private volatile boolean refreshStarted;

    public LedEngine(
            ScreenRegistry registry,
            LedConfig config,
            RefreshSource refreshSource,
            ObjectProvider<SdkRegistry> registryProvider) {
        this.registry = registry;
        this.config = config;
        this.refreshSource = refreshSource;
        this.registryProvider = registryProvider;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "led-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== 下发 ====================

    /** 下发全部屏。 */
    public DeploySummary deployAll() {
        DeploySummary summary = new DeploySummary();
        for (Screen s : registry.all()) {
            if (s.getPrograms().isEmpty()) {
                log.info("[跳过] {} 未配置节目", s.getId());
                continue;
            }
            ProgramBuilder.DeployResult r = deployScreen(s);
            summary.add(s.getId(), r);
            if (!r.success() && !config.getSystem().isContinueOnError()) {
                break;
            }
        }
        return summary;
    }

    /** 下发单个屏（走该屏对应的 SDK 实现）。 */
    public ProgramBuilder.DeployResult deployScreen(Screen s) {
        LedSdk sdk = SdkRegistry.forScreen(s, config);
        ProgramBuilder.DeployResult r = sdk.deploy(s, config);
        log.info("[下发] {} ({}) {}", s.getId(), sdk.displayName(), r);
        return r;
    }

    /** 下发单个屏的指定节目。 */
    public ProgramBuilder.DeployResult deployProgram(String screenId, int programNo) {
        Screen s = requireScreen(screenId);
        LedSdk sdk = SdkRegistry.forScreen(s, config);
        ProgramBuilder.DeployResult r = sdk.deployProgram(s, config, programNo);
        log.info("[下发节目] {} 节目 {} ({}) {}", screenId, programNo, sdk.displayName(), r);
        return r;
    }

    /** 切换屏当前播放节目。 */
    public void switchProgram(String screenId, int programIndex) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).switchProgram(s, programIndex);
        log.info("[切换] {} → 节目 {}", screenId, programIndex);
    }

    /** 检测屏是否在线。 */
    public boolean isOnline(String screenId) {
        Screen s = requireScreen(screenId);
        return SdkRegistry.forScreen(s, config).isOnline(s);
    }

    // ==================== 控制指令 ====================

    /** 开关屏。 */
    public void setPower(String screenId, boolean on) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).setPower(s, config, on);
    }

    /** 设置亮度 1-16。 */
    public void setBrightness(String screenId, int value) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).setBrightness(s, config, value);
    }

    /** 校正控制器时间。 */
    public void adjustTime(String screenId) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).adjustTime(s, config);
    }

    /** 定时亮度。 */
    public void setBrightnessTimer(String screenId) {
        Screen s = requireScreen(screenId);
        List<Integer> brightness = brightnessListOf(s);
        SdkRegistry.forScreen(s, config).setBrightnessTimer(s, config, s.getBrightnessTimer(), brightness);
    }

    /** 定时开关机。 */
    public void setPowerTimer(String screenId) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).setPowerTimer(s, config, s.getPowerTimer());
    }

    /** 设置屏参（宽/高/基色）。 */
    public void setScreenInfo(String screenId) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).setScreenInfo(s, config, s.getWidth(), s.getHeight(), s.getColorType());
    }

    /** 搜索控制器。 */
    public void searchController(String screenId, String iniFilePath, boolean broadcast) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).searchController(s, config, iniFilePath, broadcast);
    }

    /** 设置扫描参数。 */
    public void setScanLineInfo(String screenId) {
        Screen s = requireScreen(screenId);
        SdkRegistry.forScreen(s, config).setScanLineInfo(s, config);
    }

    // ==================== 刷新调度 ====================

    /** 启动所有已配置刷新的屏。 */
    public void startRefresh() {
        if (refreshStarted) {
            return;
        }
        refreshStarted = true;
        for (Map.Entry<String, RefreshConfig> e : registry.allRefreshes().entrySet()) {
            startScreenRefresh(e.getKey(), e.getValue());
        }
    }

    /** 只启动指定屏的刷新任务。 */
    public void startScreenRefresh(String screenId, RefreshConfig rc) {
        if (tasks.containsKey(screenId)) {
            return;
        }
        // 启动后立即下发首帧，避免部署完要空等一个周期才有内容
        safeRefresh(screenId);
        long period = rc.intervalMillis();
        // 用 fixed-delay 而非 fixed-rate：若单次刷新耗时 > 周期（HTTP 取数慢、卡响应慢），
        // fixed-rate 会在结束后立刻补发下一轮（追赶连发），把屏端和网络上压一堆重复帧；
        // fixed-delay 以「上次结束 + 周期」为下次起点，慢时自然降频，不堆积。
        ScheduledFuture<?> f =
                scheduler.scheduleWithFixedDelay(() -> safeRefresh(screenId), period, period, TimeUnit.MILLISECONDS);
        tasks.put(screenId, f);
        log.info(
                "[刷新] {} 已启动, 周期={}ms, 绑定数={}",
                screenId,
                period,
                rc.getBindings().size());
    }

    /** 立即刷新一次指定屏（不等定时）。 */
    public void refreshNow(String screenId) {
        safeRefresh(screenId);
    }

    /** 刷新调度是否已启动。 */
    public boolean isRefreshStarted() {
        return refreshStarted;
    }

    /** 停止全部刷新任务。 */
    public void stopRefresh() {
        for (ScheduledFuture<?> f : tasks.values()) {
            f.cancel(false);
        }
        tasks.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        refreshSource.close();
    }

    private void safeRefresh(String screenId) {
        Screen screen = registry.get(screenId);
        RefreshConfig rc = registry.refresh(screenId);
        if (screen == null || rc == null || rc.getBindings().isEmpty()) {
            return;
        }
        try {
            String frame = ProgramBuilder.neimaFrame(new ArrayList<>(rc.getBindings()), refreshSource::renderText);
            LedSdk sdk = SdkRegistry.forScreen(screen, config);
            if (!sdk.supportsNeimaRefresh()) {
                log.warn("[刷新跳过] {} ({}) 不支持内码区实时刷新，只能整屏下发", screenId, sdk.displayName());
                return;
            }
            sdk.refreshNeima(screen, frame);
            log.info("[刷新] {} → {}", screenId, frame);
        } catch (Exception e) {
            log.warn("[刷新异常] {}: {}", screenId, e.getMessage());
        }
    }

    // ==================== 校验 ====================

    /** 校验刷新配置：引用的屏必须存在，且该屏必须有对应内码区。 */
    public void validate() {
        for (Map.Entry<String, RefreshConfig> e : registry.allRefreshes().entrySet()) {
            String screenId = e.getKey();
            Screen screen = registry.get(screenId);
            if (screen == null) {
                throw new com.easywing.platform.led.sdk.LedException("refresh", -1, "刷新配置引用了不存在的屏: " + screenId);
            }
            boolean multi = screen.getPrograms().size() > 1;
            for (RefreshConfig.Binding b : e.getValue().getBindings()) {
                int pno = b.getProgramNo() != null ? b.getProgramNo() : 0;
                if (!hasNeimaIndex(screen, pno, b.getAreaIndex())) {
                    String where = multi ? ("节目 " + pno + " 内") : "该屏内";
                    log.warn(
                            "[警告] 屏 {} {}未找到内码区顺序号 {}（确认该节目已下发且含内码区{}）",
                            screenId,
                            where,
                            b.getAreaIndex(),
                            multi && b.getProgramNo() == null ? "；多节目屏建议在 binding 里显式指定 programNo" : "");
                }
            }
        }
    }

    /**
     * 判断指定节目的内码区顺序号是否存在。
     *
     * <p>{@code areaIndex} 是「内码区在节目内的添加顺序」，每节目独立从 0 编号，
     * 因此必须按节目查找，不能跨节目合并，否则多节目屏会误判。
     */
    private boolean hasNeimaIndex(Screen screen, int programNo, int areaIndex) {
        for (com.easywing.platform.led.config.Program p : screen.getPrograms()) {
            if (p.getProgramNo() != programNo) {
                continue;
            }
            for (com.easywing.platform.led.config.Area a : p.getAreas()) {
                if ("neima".equalsIgnoreCase(a.getType())
                        && a.getNeimaIndex() != null
                        && a.getNeimaIndex() == areaIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 工具 ====================

    private Screen requireScreen(String screenId) {
        Screen s = registry.get(screenId);
        if (s == null) {
            throw new com.easywing.platform.core.exception.BizException(
                    com.easywing.platform.core.exception.ErrorCode.RESOURCE_NOT_FOUND, "屏不存在: " + screenId);
        }
        return s;
    }

    private static List<Integer> brightnessListOf(Screen s) {
        List<Integer> list = new ArrayList<>();
        if (s.getBrightnessTimer() != null) {
            for (int[] g : s.getBrightnessTimer()) {
                if (g != null && g.length >= 5) {
                    list.add(g[4]);
                }
            }
        }
        return list;
    }

    /** 下发汇总。 */
    public static class DeploySummary {
        private final List<String> lines = new ArrayList<>();
        private int ok;
        private int failed;

        void add(String screenId, ProgramBuilder.DeployResult r) {
            lines.add(screenId + ": 成功 " + r.ok + ", 失败 " + r.failed);
            ok += r.ok;
            failed += r.failed;
        }

        public int ok() {
            return ok;
        }

        public int failed() {
            return failed;
        }

        public boolean allSuccess() {
            return failed == 0;
        }

        public List<String> lines() {
            return lines;
        }

        @Override
        public String toString() {
            return "总计: 成功 " + ok + " 个节目, 失败 " + failed + " 个";
        }
    }
}
