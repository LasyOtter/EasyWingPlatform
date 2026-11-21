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

import com.easywing.platform.led.engine.LedEngine;
import com.easywing.platform.led.engine.RefreshSource;
import com.easywing.platform.led.engine.ScreenRegistry;
import com.easywing.platform.led.sdk.LedSdk;
import com.easywing.platform.led.sdk.SdkRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * LED 服务装配：条件加载 YAML 配置、构建 {@link ScreenRegistry} 与 {@link LedEngine}。
 *
 * <p>仅当 {@code easywing.led.enabled=true}（默认）时生效；
 * {@code easywing.led.file-config-enabled=false} 可跳过 YAML 加载（纯数据库管理模式）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LedConfigProperties.class)
@ConditionalOnProperty(prefix = "easywing.led", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LedServiceBootstrap {

    /** 根配置 + 屏注册表 + 刷新数据源。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "easywing.led",
            name = "file-config-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ScreenRegistry ledScreenRegistry(ConfigLoader loader, LedConfigProperties properties) {
        LedConfig config = loader.loadRoot();
        ScreenRegistry registry = new ScreenRegistry(java.nio.file.Path.of(properties.getBaseDir()), config, loader);
        log.info(
                "[LED] 已加载 {} 个屏定义, 刷新配置 {} 个",
                registry.all().size(),
                registry.allRefreshes().size());
        return registry;
    }

    /** 刷新数据源桥接（依赖注册表）。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "easywing.led",
            name = "file-config-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RefreshSource ledRefreshSource(ScreenRegistry registry) {
        RefreshSource source = new RefreshSource(registry.config());
        log.info("[LED] 刷新数据源就绪: {}", source.describe());
        return source;
    }

    /** 引擎：下发 + 刷新调度。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "easywing.led",
            name = "file-config-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public LedEngine ledEngine(
            ScreenRegistry registry,
            RefreshSource refreshSource,
            org.springframework.beans.factory.ObjectProvider<SdkRegistry> registryProvider) {
        LedConfig config = registry.config();
        return new LedEngine(registry, config, refreshSource, registryProvider);
    }

    /** 应用就绪后：初始化 SDK、校验刷新配置，按开关自动下发/启动刷新。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(LedEngine engine, ScreenRegistry registry, LedConfigProperties properties) {
        for (LedSdk sdk : SdkRegistry.instances()) {
            try {
                sdk.init(registry.config());
                log.info("[LED] SDK 就绪: {} ({})", sdk.provider(), sdk.displayName());
            } catch (Throwable t) {
                log.warn("[LED] SDK {} 初始化失败，已跳过: {}", sdk.provider(), t.getMessage());
            }
        }
        try {
            engine.validate();
        } catch (RuntimeException e) {
            log.warn("[LED] 刷新配置校验未通过: {}", e.getMessage());
        }
        if (properties.isAutoDeploy()) {
            log.info("[LED] auto-deploy 已开启，开始下发全部屏");
            engine.deployAll();
        }
        if (properties.isAutoRefresh()) {
            log.info("[LED] auto-refresh 已开启，启动内码刷新调度");
            engine.startRefresh();
        }
    }

    /** 优雅停机：停止刷新调度。 */
    @PreDestroy
    public void onShutdown(LedEngine engine) {
        try {
            engine.stopRefresh();
        } catch (Exception ignored) {
            // 停机阶段忽略
        }
    }
}
