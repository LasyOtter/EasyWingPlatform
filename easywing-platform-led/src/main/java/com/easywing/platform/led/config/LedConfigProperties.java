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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LED 服务配置（{@code easywing.led.*}）。
 *
 * <p>保留 YAML 文件驱动的灵活性：{@code baseDir} 指向工程根目录，
 * 服务启动时从 {@code <baseDir>/config/led.yml} 加载 {@link LedConfig} 与三层屏/刷新定义；
 * 同时把关键开关暴露为 Spring 配置，便于容器化部署覆盖。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.led")
public class LedConfigProperties {

    /** 工程根目录（含 config/led.yml），默认当前工作目录。 */
    private String baseDir = ".";

    /** 是否启用 LED 服务（含刷新调度）。默认 true，设为 false 可不加载任何硬件 SDK。 */
    private boolean enabled = true;

    /** 是否加载 YAML 文件配置。默认 true；纯数据库管理模式可关闭。 */
    private boolean fileConfigEnabled = true;

    /** 启动时是否自动下发所有屏节目。默认 false（由接口显式触发）。 */
    private boolean autoDeploy = false;

    /** 启动时是否启动内码刷新调度。默认 false。 */
    private boolean autoRefresh = false;
}
