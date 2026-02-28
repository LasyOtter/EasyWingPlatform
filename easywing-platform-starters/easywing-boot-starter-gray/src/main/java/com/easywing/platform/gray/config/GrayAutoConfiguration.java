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
package com.easywing.platform.gray.config;

import com.easywing.platform.gray.properties.GrayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 灰度发布自动配置
 * <p>
 * 支持基于版本、权重、用户ID等多种灰度策略
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(GrayProperties.class)
@ConditionalOnProperty(prefix = "easywing.gray", name = "enabled", havingValue = "true")
public class GrayAutoConfiguration {

    @Bean
    public GrayProperties grayProperties() {
        log.info("EasyWing Gray Release Configuration initialized");
        return new GrayProperties();
    }

    public GrayAutoConfiguration() {
        log.info("EasyWing Gray AutoConfiguration initialized");
    }
}
