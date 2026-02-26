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

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.easywing.platform.data.properties.DataProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * MyBatis-Plus 数据访问层自动配置
 * <p>
 * 提供以下功能：
 * <ul>
 *     <li>MyBatis-Plus 插件配置（分页、乐观锁、多租户）</li>
 *     <li>审计字段自动填充</li>
 *     <li>动态数据源支持（读写分离）</li>
 * </ul>
 * <p>
 * 配置示例：
 * <pre>
 * # application.yml
 * easywing:
 *   data:
 *     # 多租户配置
 *     tenant-enabled: true
 *     tenant-ignore-tables:
 *       - sys_user
 *       - sys_role
 *
 *     # 审计字段配置
 *     audit-enabled: true
 *
 *     # 分页配置
 *     pagination:
 *       max-limit: 1000
 *       overflow: true
 *
 * spring:
 *   datasource:
 *     # Druid 配置
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@ConditionalOnClass(com.baomidou.mybatisplus.core.metadata.IPage.class)
@EnableConfigurationProperties(DataProperties.class)
@Import({MyBatisPlusConfig.class, DynamicDataSourceConfig.class})
@MapperScan("${mybatis-plus.mapper-scan:com.**.mapper}")
public class MyBatisPlusAutoConfiguration {

    public MyBatisPlusAutoConfiguration() {
        log.info("EasyWing MyBatis-Plus AutoConfiguration initialized");
    }
}
