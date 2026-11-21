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
package com.easywing.platform.led.sdk.vision;

import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.engine.ProgramBuilder;
import com.easywing.platform.led.sdk.LedSdk;
import com.easywing.platform.led.sdk.SdkGateway;
import com.listenvision.led;
import org.springframework.stereotype.Component;

/**
 * 灵信 1.0.8 SDK 适配（lv_led_64.dll，{@code com.listenvision.led}）。
 *
 * <p>能力：整屏下发 + 内码区实时刷新（%disp 协议）+ 节目切换 + 在线检测。
 * 定时/亮度/开关机/搜卡等控制指令该 DLL 未导出，故不支持。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public final class VisionSdk implements LedSdk {

    private final ProgramBuilder builder = new ProgramBuilder();

    @Override
    public String provider() {
        return "vision";
    }

    @Override
    public void init(LedConfig config) {
        SdkGateway.init(config);
    }

    @Override
    public boolean initialized() {
        return SdkGateway.isInitialized();
    }

    @Override
    public ProgramBuilder.DeployResult deploy(Screen screen, LedConfig config) {
        return builder.deploy(screen, config);
    }

    @Override
    public ProgramBuilder.DeployResult deployProgram(Screen screen, LedConfig config, int programNo) {
        return builder.deployProgram(screen, config, programNo);
    }

    @Override
    public void switchProgram(Screen screen, int programIndex) {
        int code = SdkGateway.callReturn(
                "ProgramSwitch(" + screen.getIp() + ", " + programIndex + ")",
                () -> led.ProgramSwitch(screen.getIp(), programIndex));
    }

    @Override
    public boolean isOnline(Screen screen) {
        try {
            return led.TestOnline(screen.getIp()) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean supportsNeimaRefresh() {
        return true;
    }

    @Override
    public void refreshNeima(Screen screen, String frame) {
        SdkGateway.call("RefreshNeiMaArea(" + screen.getIp() + ")", () -> led.RefreshNeiMaArea(screen.getIp(), frame));
    }

    @Override
    public java.nio.charset.Charset encoding(LedConfig config) {
        return SdkGateway.encoding();
    }

    @Override
    public String displayName() {
        return "灵信 1.0.8 (lv_led_64.dll)";
    }
}
