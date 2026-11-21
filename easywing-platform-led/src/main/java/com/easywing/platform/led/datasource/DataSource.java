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
package com.easywing.platform.led.datasource;

/**
 * 数据源 SPI：一种数据来源一个实现。
 *
 * <p>通过 {@link DataSourceType#register(String, Factory)} 注册，
 * 配置里用 {@code type} 选择。内建类型：
 * <ul>
 *   <li>{@code counter} 本地计数器（演示/联调）</li>
 *   <li>{@code now}     当前时间</li>
 *   <li>{@code http}    HTTP/REST 取数</li>
 *   <li>{@code jdbc}    关系型数据库（MySQL/PostgreSQL/SQL Server）</li>
 *   <li>{@code script}  JavaScript 表达式</li>
 *   <li>{@code static}  固定文本</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface DataSource {

    /** 类型标识，如 jdbc / http。 */
    String type();

    /** 数据源 id（配置里的 source 名，用于日志与统计）。 */
    String id();

    /** 取一批数据。 */
    DataRow.Result fetch(FetchContext ctx);

    /** 释放连接等资源；默认空实现。 */
    default void close() {}

    /** 工厂：按配置参数创建数据源实例。 */
    interface Factory {
        DataSource create(String id, DataSourceSpec spec);
    }

    /** 数据源配置规格（由配置层解析后传入）。 */
    record DataSourceSpec(String type, String id, java.util.Map<String, Object> params) {}
}
