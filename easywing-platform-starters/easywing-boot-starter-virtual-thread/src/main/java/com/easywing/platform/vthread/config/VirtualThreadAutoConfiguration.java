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
package com.easywing.platform.vthread.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;

import java.util.concurrent.Executors;

/**
 * 虚拟线程自动配置
 * <p>
 * 为Java 21+项目提供虚拟线程支持
 * <p>
 * 启用方式：
 * <ul>
 *     <li>配置：spring.threads.virtual.enabled=true</li>
 *     <li>Tomcat：server.tomcat.threads.virtual=true</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@AutoConfiguration(before = TaskExecutionAutoConfiguration.class)
@EnableConfigurationProperties(VirtualThreadProperties.class)
@ConditionalOnProperty(prefix = "spring.threads.virtual", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VirtualThreadAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "spring.threads.virtual", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AsyncTaskExecutor applicationTaskExecutor() {
        log.info("Enabling virtual threads for async task execution");
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean(name = AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME)
    @ConditionalOnProperty(prefix = "spring.threads.virtual", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AsyncTaskExecutor asyncTaskExecutor() {
        log.info("Enabling virtual threads for @Async annotation");
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
