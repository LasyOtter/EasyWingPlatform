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
 * 多级缓存注解
 * <p>
 * 支持本地缓存(Caffeine) + 分布式缓存(Redis)的多级缓存架构
 * <pre>
 * 查询流程：请求 -> 本地缓存 -> Redis -> 数据库
 * 更新流程：更新数据库 -> 删除Redis -> 广播清理本地缓存
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MultiLevelCache {

    /**
     * 缓存名称
     */
    String value();

    /**
     * 缓存key（SpEL表达式）
     * <p>
     * 支持的SpEL表达式：
     * <ul>
     *     <li>#userId - 方法参数</li>
     *     <li>#user.id - 对象属性</li>
     *     <li>#p0 - 第一个参数</li>
     *     <li>#result - 返回值</li>
     * </ul>
     */
    String key() default "";

    /**
     * 本地缓存过期时间（秒）
     * <p>
     * 默认60秒，建议设置为较短时间以保证数据一致性
     */
    int localExpire() default 60;

    /**
     * Redis缓存过期时间（秒）
     * <p>
     * 默认300秒（5分钟）
     */
    int redisExpire() default 300;

    /**
     * 是否缓存null值
     * <p>
     * 用于防止缓存穿透，默认不缓存null
     */
    boolean cacheNull() default false;

    /**
     * 缓存key的生成策略
     */
    KeyGenerator keyGenerator() default KeyGenerator.DEFAULT;

    /**
     * Key生成策略枚举
     */
    enum KeyGenerator {
        /**
         * 默认策略：使用cacheName + key表达式
         */
        DEFAULT,
        /**
         * 简单策略：仅使用key表达式
         */
        SIMPLE,
        /**
         * 参数哈希策略：对所有参数进行哈希生成key
         */
        PARAMS_HASH
    }
}
