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
package com.easywing.platform.data.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源自动配置
 * <p>
 * 支持多数据源配置和读写分离
 * <p>
 * 配置示例：
 * <pre>
 * easywing:
 *   datasource:
 *     dynamic:
 *       enabled: true
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
@ConditionalOnProperty(prefix = "easywing.datasource.dynamic", name = "enabled", havingValue = "true")
public class DynamicDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DynamicDataSourceProperties properties,
                                  HikariDataSource masterDataSource,
                                  HikariDataSource slaveDataSource) {
        DynamicRoutingDataSource dynamicDataSource = new DynamicRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DynamicDataSourceContextHolder.MASTER, masterDataSource);
        log.info("Registered master data source");

        if (properties.isSlaveEnabled() && slaveDataSource != null) {
            dataSourceMap.put(DynamicDataSourceContextHolder.SLAVE, slaveDataSource);
            log.info("Registered slave data source for read/write separation");
        }

        dynamicDataSource.setTargetDataSources(dataSourceMap);
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);

        log.info("Dynamic data source initialized with {} data sources", dataSourceMap.size());
        return dynamicDataSource;
    }
}
