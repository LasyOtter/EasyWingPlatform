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

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

/**
 * 处理器类型谓词
 * <p>
 * 用于判断Controller类是否匹配特定条件，支持路径前缀配置。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface HandlerTypePredicate extends Predicate<Class<?>> {

    /**
     * 创建带有指定注解的谓词
     *
     * @param annotationType 注解类型
     * @return 谓词实例
     */
    static HandlerTypePredicate forAnnotation(Class<? extends Annotation> annotationType) {
        return clazz -> AnnotatedElementUtils.hasAnnotation(clazz, annotationType);
    }
}
