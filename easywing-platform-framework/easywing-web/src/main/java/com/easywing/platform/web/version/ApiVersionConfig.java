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
package com.easywing.platform.web.version;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * API版本控制配置
 * <p>
 * 为带有 {@link ApiVersion} 注解的Controller自动添加版本前缀。
 * 默认启用，可通过配置关闭。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "easywing.web.api-version", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiVersionConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api/";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX + "v1", HandlerTypePredicate.forAnnotation(ApiVersion.class));
        log.info("API Version control enabled, prefix: {}{}", API_PREFIX, "v1");
    }
}
