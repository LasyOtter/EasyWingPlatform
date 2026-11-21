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
package com.easywing.platform.led.sdk;

import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.engine.ProgramBuilder;
import java.util.List;

/**
 * LED 控制卡 SDK 适配层（SPI）。
 *
 * <p>每种 SDK 体系实现一个 {@code LedSdk}：
 * <ul>
 *   <li>{@code vision} —— 灵信 1.0.8（{@code lv_led_64.dll}，句柄式建节目 + 整屏下发 + 内码实时刷新）</li>
 *   <li>{@code listen} —— 灵信五代 V2014（{@code ListenPlayDll.dll}，会话句柄 + 整屏下发，不支持内码实时刷新）</li>
 *   <li>{@code onbon} —— 仰邦 bx06（纯 Java socket，动态区实时刷新）</li>
 * </ul>
 *
 * <p>屏配置通过 {@code screen.sdkProvider}（或 {@code screen.ledType} 回退）选择实现，
 * 默认走根配置 {@code sdk.provider}（未指定即 {@code vision}）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface LedSdk {

    /** 实现标识，与配置 sdk.provider / screen.sdkProvider 对应。 */
    String provider();

    /** 初始化（加载 DLL、设置卡型号等），整个进程对每个实现只调用一次。 */
    void init(LedConfig config);

    /** 是否已初始化。 */
    boolean initialized();

    /**
     * 下发单个屏的全部节目。
     *
     * @return 成功/失败节目数
     */
    ProgramBuilder.DeployResult deploy(Screen screen, LedConfig config);

    /**
     * 下发单个节目（部分 SDK 需要整体下发，此时等价于 deploy）。
     */
    default ProgramBuilder.DeployResult deployProgram(Screen screen, LedConfig config, int programNo) {
        return deploy(screen, config);
    }

    /**
     * 切换屏当前播放节目。不支持的实现应抛 {@link LedException}。
     */
    default void switchProgram(Screen screen, int programIndex) {
        throw new LedException(
                "ProgramSwitch(" + screen.getIp() + ", " + programIndex + ")", -1, provider() + " 不支持节目切换");
    }

    /** 检测屏是否在线。 */
    default boolean isOnline(Screen screen) {
        return false;
    }

    /** 设置屏参（宽度/高度/基色）。 */
    default void setScreenInfo(Screen screen, LedConfig config, int width, int height, int colorType) {
        throw new LedException("SetScreenInfo(" + screen.getIp() + ")", -1, provider() + " 不支持屏参设置");
    }

    /** 校正控制器时间（把 PC 时间下发到屏）。 */
    default void adjustTime(Screen screen, LedConfig config) {
        throw new LedException("AdjustTime(" + screen.getIp() + ")", -1, provider() + " 不支持校正时间");
    }

    /** 开关屏：{@code on} 为 true 开屏。 */
    default void setPower(Screen screen, LedConfig config, boolean on) {
        throw new LedException("SetPower(" + screen.getIp() + ")", -1, provider() + " 不支持开关屏");
    }

    /** 设置亮度 1-16。 */
    default void setBrightness(Screen screen, LedConfig config, int value) {
        throw new LedException("SetBrightness(" + screen.getIp() + ")", -1, provider() + " 不支持亮度设置");
    }

    /**
     * 定时亮度（最多 3 组）。
     *
     * <p>每组 5 元 {@code {startHour, startMinute, endHour, endMinute, brightness}}，
     * 与 {@code Screen.brightnessTimer} 一一对应；{@code brightness} 为等长亮度列表，
     * 缺省按 1 处理。
     */
    default void setBrightnessTimer(Screen screen, LedConfig config, List<int[]> groups, List<Integer> brightness) {
        throw new LedException("SetBrightnessTimer(" + screen.getIp() + ")", -1, provider() + " 不支持定时亮度");
    }

    /**
     * 定时开关机（最多 3 组）。
     *
     * <p>每组 4 元 {@code {onHour, onMinute, offHour, offMinute}}，与 {@code Screen.powerTimer} 对应。
     * 传空列表表示清除全部定时。
     */
    default void setPowerTimer(Screen screen, LedConfig config, List<int[]> groups) {
        throw new LedException("SetPowerTimer(" + screen.getIp() + ")", -1, provider() + " 不支持定时开关机");
    }

    /** 搜索控制器，把结果写入 ini 文件。 */
    default void searchController(Screen screen, LedConfig config, String iniFilePath, boolean broadcast) {
        throw new LedException("SearchController(" + screen.getIp() + ")", -1, provider() + " 不支持搜索控制器");
    }

    /** 设置扫描参数（E 系列等需要的模组/扫参）。 */
    default void setScanLineInfo(Screen screen, LedConfig config) {
        throw new LedException("SetScanLineInfo(" + screen.getIp() + ")", -1, provider() + " 不支持扫描参数");
    }

    /** 是否支持内码区实时刷新（%disp 协议）。 */
    default boolean supportsNeimaRefresh() {
        return false;
    }

    /**
     * 下发一帧内码刷新文本（%disp 协议）。
     *
     * @throws LedException 实现不支持内码刷新时抛出
     */
    default void refreshNeima(Screen screen, String frame) {
        throw new LedException("RefreshNeiMaArea(" + screen.getIp() + ")", -1, provider() + " 不支持内码实时刷新");
    }

    /** DLL 期望的字符串编码。 */
    default java.nio.charset.Charset encoding(LedConfig config) {
        return java.nio.charset.Charset.defaultCharset();
    }

    /** SDK 名称，用于日志。 */
    default String displayName() {
        return provider();
    }
}
