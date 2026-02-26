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

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.easywing.platform.data.interceptor.TenantLineInnerInterceptor;
import com.easywing.platform.data.properties.DataProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置分页插件、乐观锁插件、多租户插件等
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(DataProperties.class)
@MapperScan("${mybatis-plus.mapper-scan:com.**.mapper}")
public class MyBatisPlusConfig {

    private final DataProperties dataProperties;

    public MyBatisPlusConfig(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
    }

    /**
     * MyBatis-Plus 插件配置
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        log.info("Initializing MyBatis-Plus interceptor");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(dataProperties.getPagination().getMaxLimit());
        paginationInnerInterceptor.setOverflow(dataProperties.getPagination().isOverflow());
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        log.info("MyBatis-Plus pagination plugin configured: maxLimit={}, overflow={}",
                dataProperties.getPagination().getMaxLimit(),
                dataProperties.getPagination().isOverflow());

        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        log.info("MyBatis-Plus optimistic lock plugin configured");

        // 多租户插件
        if (dataProperties.isTenantEnabled()) {
            TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor(dataProperties);
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor);
            log.info("MyBatis-Plus tenant plugin configured: tenantIdColumn={}, ignoreTables={}",
                    dataProperties.getTenantIdColumn(),
                    dataProperties.getTenantIgnoreTables());
        } else {
            log.info("MyBatis-Plus tenant plugin is disabled");
        }

        return interceptor;
    }

    /**
     * 审计字段自动填充处理器
     * <p>
     * 仅在未配置其他MetaObjectHandler时生效
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.data", name = "audit-enabled", havingValue = "true", matchIfMissing = true)
    public MetaObjectHandler auditMetaObjectHandler(DataProperties properties) {
        return new com.easywing.platform.data.handler.AuditMetaObjectHandler(properties);
    }
}
