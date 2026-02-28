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
package com.easywing.platform.web.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流保护注解
 * <p>
 * 基于Redis令牌桶算法实现分布式限流，保护接口免受过多请求冲击。
 * <p>
 * 示例：
 * <pre>
 * &#64;PostMapping("/login")
 * &#64;RateLimit(key = "#request.username", rate = 5, capacity = 5, message = "登录尝试过于频繁")
 * public ResponseEntity&lt;TokenResponse&gt; login(@RequestBody LoginRequest request) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key的SPEL表达式
     * <p>
     * 支持SPEL表达式，可使用方法参数：
     * <ul>
     *     <li>#request.username - 根据用户名限流</li>
     *     <li>#id - 根据ID限流</li>
     * </ul>
     * 为空时默认使用客户端IP地址
     *
     * @return SPEL表达式
     */
    String key() default "";

    /**
     * 每秒允许请求数（速率）
     * <p>
     * 令牌桶的填充速率。
     *
     * @return 速率，默认10.0
     */
    double rate() default 10.0;

    /**
     * 突发容量（桶大小）
     * <p>
     * 令牌桶的最大容量，允许短时间内突发请求。
     *
     * @return 容量，默认20
     */
    int capacity() default 20;

    /**
     * 提示消息
     * <p>
     * 限流触发时返回的友好提示。
     *
     * @return 提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}
