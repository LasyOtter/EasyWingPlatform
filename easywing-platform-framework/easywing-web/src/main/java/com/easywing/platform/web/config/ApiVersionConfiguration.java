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
package com.easywing.platform.web.config;

import com.easywing.platform.web.annotation.ApiVersion;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.condition.*;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * API 版本配置
 * <p>
 * 自动处理 @ApiVersion 注解，实现基于 URL 路径的版本控制
 * <p>
 * 版本化 URL 格式: /api/v{version}/{resource}
 * 例如: /api/v1/users, /api/v2/users
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
public class ApiVersionConfiguration implements WebMvcRegistrations {

    /**
     * API 版本路径前缀
     */
    private static final String VERSION_PREFIX = "/v";

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiVersionRequestMappingHandlerMapping();
    }

    /**
     * 自定义请求映射处理器
     * <p>
     * 处理 @ApiVersion 注解，将版本号添加到 URL 路径中
     */
    private static class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

        @Override
        protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
            RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
            if (info == null) {
                return null;
            }

            // 检查方法级别的 @ApiVersion
            ApiVersion methodVersion = AnnotationUtils.findAnnotation(method, ApiVersion.class);
            if (methodVersion != null) {
                return createVersionedRequestMappingInfo(info, methodVersion);
            }

            // 检查类级别的 @ApiVersion
            ApiVersion classVersion = AnnotationUtils.findAnnotation(handlerType, ApiVersion.class);
            if (classVersion != null) {
                return createVersionedRequestMappingInfo(info, classVersion);
            }

            return info;
        }

        /**
         * 创建版本化的请求映射信息
         */
        private RequestMappingInfo createVersionedRequestMappingInfo(RequestMappingInfo info, ApiVersion apiVersion) {
            String version = apiVersion.value();

            // 构建版本路径模式
            PatternsRequestCondition patternsCondition = new PatternsRequestCondition(
                    VERSION_PREFIX + version + info.getPatternValues().toArray()[0]
            );

            // 合并其他条件
            return new RequestMappingInfo(
                    info.getName(),
                    patternsCondition,
                    info.getMethodsCondition(),
                    info.getParamsCondition(),
                    info.getHeadersCondition(),
                    info.getConsumesCondition(),
                    info.getProducesCondition(),
                    info.getCustomCondition()
            );
        }
    }
}
