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
package com.easywing.platform.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多级缓存清理注解
 * <p>
 * 用于标记需要清理缓存的方法，会同时清理本地缓存和Redis缓存
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

    /**
     * 缓存名称
     */
    String value();

    /**
     * 缓存key（SpEL表达式）
     */
    String key() default "";

    /**
     * 是否清理所有缓存项
     */
    boolean allEntries() default false;

    /**
     * 是否在方法执行前清理缓存
     * <p>
     * 默认为false，即方法执行成功后才清理缓存
     */
    boolean beforeInvocation() default false;
}
