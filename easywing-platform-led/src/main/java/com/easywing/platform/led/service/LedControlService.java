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
package com.easywing.platform.led.service;

import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.LedConfigProperties;
import com.easywing.platform.led.domain.vo.LedDeployResultVO;
import com.easywing.platform.led.domain.vo.LedStatusVO;
import com.easywing.platform.led.engine.LedEngine;
import com.easywing.platform.led.engine.ProgramBuilder;
import com.easywing.platform.led.engine.RefreshSource;
import com.easywing.platform.led.engine.ScreenRegistry;
import com.easywing.platform.led.sdk.LedSdk;
import com.easywing.platform.led.sdk.SdkRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * LED 运控服务：下发、切节目、开关屏、亮度、刷新等操作的应用层编排。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LedControlService {

    private final LedEngine engine;
    private final ScreenRegistry registry;
    private final RefreshSource refreshSource;
    private final LedConfigProperties properties;
    private final LedScreenService screenService;

    /** 服务状态。 */
    public LedStatusVO status() {
        LedStatusVO vo = new LedStatusVO();
        vo.setEnabled(properties.isEnabled());
        vo.setFileConfigEnabled(properties.isFileConfigEnabled());
        vo.setAutoDeploy(properties.isAutoDeploy());
        vo.setAutoRefresh(properties.isAutoRefresh());
        vo.setRefreshStarted(engine.isRefreshStarted());
        vo.setSdkProviders(SdkRegistry.available());
        vo.setScreens(
                registry.all().stream().map(s -> s.getId() + "/" + s.getName()).toList());
        vo.setRefreshes(registry.allRefreshes().keySet().stream().toList());
        vo.setNamedDataSources(refreshSource.describe());
        return vo;
    }

    /** 全部屏下发。 */
    public LedDeployResultVO deployAll() {
        LedEngine.DeploySummary summary = engine.deployAll();
        record(summary);
        return toVO(summary);
    }

    /** 单屏下发。 */
    public LedDeployResultVO deployScreen(String screenId) {
        ProgramBuilder.DeployResult r = engine.deployScreen(requireScreen(screenId));
        screenService.recordDeploy(screenId, r.failed == 0 ? 0 : (r.ok == 0 ? 2 : 1), r.lastError);
        return toVO(r);
    }

    /** 单屏单节目下发。 */
    public LedDeployResultVO deployProgram(String screenId, int programNo) {
        ProgramBuilder.DeployResult r = engine.deployProgram(screenId, programNo);
        screenService.recordDeploy(screenId, r.failed == 0 ? 0 : (r.ok == 0 ? 2 : 1), r.lastError);
        return toVO(r);
    }

    /** 切节目。 */
    public void switchProgram(String screenId, int programIndex) {
        engine.switchProgram(screenId, programIndex);
    }

    /** 在线检测。 */
    public boolean online(String screenId) {
        boolean on = engine.isOnline(screenId);
        screenService.recordOnline(screenId, on);
        return on;
    }

    /** 开关屏。 */
    public void power(String screenId, boolean on) {
        engine.setPower(screenId, on);
    }

    /** 亮度。 */
    public void brightness(String screenId, int value) {
        engine.setBrightness(screenId, value);
    }

    /** 校时。 */
    public void adjustTime(String screenId) {
        engine.adjustTime(screenId);
    }

    /** 定时亮度。 */
    public void brightnessTimer(String screenId) {
        engine.setBrightnessTimer(screenId);
    }

    /** 定时开关机。 */
    public void powerTimer(String screenId) {
        engine.setPowerTimer(screenId);
    }

    /** 屏参。 */
    public void screenInfo(String screenId) {
        engine.setScreenInfo(screenId);
    }

    /** 扫描参数。 */
    public void scanLineInfo(String screenId) {
        engine.setScanLineInfo(screenId);
    }

    /** 搜索控制器。 */
    public void searchController(String screenId, String iniFilePath, boolean broadcast) {
        engine.searchController(screenId, iniFilePath, broadcast);
    }

    /** 立即刷新一次。 */
    public void refreshNow(String screenId) {
        engine.refreshNow(screenId);
    }

    /** 启动刷新调度。 */
    public void startRefresh() {
        engine.startRefresh();
    }

    /** 校验刷新配置。 */
    public void validateRefresh() {
        engine.validate();
    }

    /** 指定屏使用的 SDK 实现名。 */
    public String providerOf(String screenId) {
        return registry.get(screenId).effectiveProvider(config());
    }

    /** 指定屏的 SDK 支持的内码刷新能力。 */
    public boolean supportsNeimaRefresh(String screenId) {
        LedSdk sdk = SdkRegistry.forScreen(requireScreen(screenId), config());
        return sdk.supportsNeimaRefresh();
    }

    private com.easywing.platform.led.config.Screen requireScreen(String screenId) {
        com.easywing.platform.led.config.Screen s = registry.get(screenId);
        if (s == null) {
            throw new com.easywing.platform.core.exception.BizException(
                    com.easywing.platform.core.exception.ErrorCode.RESOURCE_NOT_FOUND, "屏不存在: " + screenId);
        }
        return s;
    }

    private LedConfig config() {
        return registry.config();
    }

    private void record(LedEngine.DeploySummary summary) {
        // 汇总结果按屏回写
        for (String screenId : registry.all().stream()
                .map(com.easywing.platform.led.config.Screen::getId)
                .toList()) {
            // 汇总对象未按屏保留成功/失败细节，这里只回写屏级状态
            screenService.recordDeploy(screenId, summary.allSuccess() ? 0 : 2, null);
        }
    }

    private LedDeployResultVO toVO(ProgramBuilder.DeployResult r) {
        LedDeployResultVO vo = new LedDeployResultVO();
        vo.setOk(r.ok);
        vo.setFailed(r.failed);
        vo.setLastError(r.lastError);
        return vo;
    }

    private LedDeployResultVO toVO(LedEngine.DeploySummary s) {
        LedDeployResultVO vo = new LedDeployResultVO();
        vo.setOk(s.ok());
        vo.setFailed(s.failed());
        vo.setLines(s.lines());
        return vo;
    }
}
