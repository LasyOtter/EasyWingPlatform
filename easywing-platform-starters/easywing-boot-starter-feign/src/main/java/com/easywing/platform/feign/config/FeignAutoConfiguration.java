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
package com.easywing.platform.feign.config;

import com.easywing.platform.feign.interceptor.HeaderPropagationInterceptor;
import com.easywing.platform.feign.properties.FeignProperties;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Feign 自动配置
 * <p>
 * 提供以下增强功能:
 * <ul>
 *     <li>请求头传递(Trace-Id, Authorization等)</li>
 *     <li>统一超时配置</li>
 *     <li>请求日志记录</li>
 *     <li>重试策略</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(before = FeignAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(prefix = "easywing.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignAutoConfiguration {

    /**
     * Feign 请求选项
     */
    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignOptions(FeignProperties properties) {
        return new Request.Options(
                properties.getConnectTimeout(), TimeUnit.MILLISECONDS,
                properties.getReadTimeout(), TimeUnit.MILLISECONDS,
                true
        );
    }

    /**
     * 请求头传递拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.feign", name = "propagate-headers", havingValue = "true", matchIfMissing = true)
    public RequestInterceptor headerPropagationInterceptor(FeignProperties properties) {
        log.info("EasyWing Feign Header Propagation Interceptor initialized");
        return new HeaderPropagationInterceptor(properties.getPropagateHeaderNames());
    }

    /**
     * Feign 日志级别
     */
    @Bean
    @ConditionalOnProperty(prefix = "easywing.feign", name = "logging-enabled", havingValue = "true", matchIfMissing = true)
    public Logger.Level feignLoggerLevel(FeignProperties properties) {
        return Logger.Level.valueOf(properties.getLogLevel().toUpperCase());
    }

    /**
     * Feign 重试器(默认不重试)
     */
    @Bean
    @ConditionalOnMissingBean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    public FeignAutoConfiguration() {
        log.info("EasyWing Feign AutoConfiguration initialized");
    }
}
