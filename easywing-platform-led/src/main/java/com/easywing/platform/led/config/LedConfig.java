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
package com.easywing.platform.led.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * LED 根配置（led.yml）。
 *
 * <pre>
 * sdk:
 *   provider: vision
 *   dllPath: sdk/lv_led_64.dll
 * system:
 *   screensDir: config/screens
 *   refreshDir: config/refresh
 * sources:
 *   notice:
 *     type: jdbc
 *     jdbcUrl: jdbc:mysql://...
 *     sql: SELECT ...
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class LedConfig {

    private Sdk sdk = new Sdk();
    private System system = new System();
    /** 命名数据源，键为 source id。 */
    private Map<String, SourceDef> sources = new LinkedHashMap<>();

    @Data
    public static class Sdk {
        /** 默认 SDK 实现：vision（1.0.8）/ listen（五代 V2014）/ onbon（仰邦 bx06）。 */
        private String provider = "vision";
        /** 1.0.8 DLL 路径。 */
        private String dllPath = "sdk/lv_led_64.dll";

        private int ledType = 3;
        private int rgb = 0;
        private String encoding = "GBK";
        /** V2014 动态库路径（相对工程根目录或绝对路径）。 */
        private String listenDllPath = "sdk/ListenPlayDll.dll";
        /** V2014 网络端口，默认 10000。 */
        private int listenPort = 10000;
    }

    @Data
    public static class System {
        private int defaultSaveType = 0;
        private String screensDir = "config/screens";
        private String refreshDir = "config/refresh";
        private boolean continueOnError = true;
        private String logLevel = "INFO";
    }

    /**
     * 单个数据源定义。
     */
    @Data
    public static class SourceDef {
        private String type;
        private Map<String, Object> params = new LinkedHashMap<>();
    }
}
