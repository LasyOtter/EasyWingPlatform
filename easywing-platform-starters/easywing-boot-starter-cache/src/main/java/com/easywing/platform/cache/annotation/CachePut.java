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
 * 多级缓存更新注解
 * <p>
 * 用于更新缓存内容，总是执行方法并将结果存入缓存
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CachePut {

    /**
     * 缓存名称
     */
    String value();

    /**
     * 缓存key（SpEL表达式）
     */
    String key() default "";

    /**
     * 本地缓存过期时间（秒）
     */
    int localExpire() default 60;

    /**
     * Redis缓存过期时间（秒）
     */
    int redisExpire() default 300;

    /**
     * 是否缓存null值
     */
    boolean cacheNull() default false;

    /**
     * 条件表达式（SpEL），为true时才更新缓存
     */
    String condition() default "";

    /**
     * 除非表达式（SpEL），为true时不更新缓存
     */
    String unless() default "";
}
