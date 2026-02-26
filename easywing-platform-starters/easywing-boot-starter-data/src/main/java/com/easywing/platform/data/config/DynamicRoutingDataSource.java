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

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由
 * <p>
 * 支持读写分离，根据执行类型自动切换数据源
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        String dataSourceKey = DynamicDataSourceContextHolder.getDataSourceKey();
        log.debug("Current data source: {}", dataSourceKey);
        return dataSourceKey;
    }
}
