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
package com.easywing.platform.web.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性保护注解
 * <p>
 * 用于防止重复提交导致的业务问题，基于Redis分布式锁实现。
 * 适用于创建、修改、删除等敏感操作。
 * <p>
 * 示例：
 * <pre>
 * &#64;PostMapping
 * &#64;Idempotent(key = "#userDTO.username", expire = 30, message = "该用户正在创建中")
 * public ResponseEntity&lt;Long&gt; add(@RequestBody UserDTO userDTO) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等键SPEL表达式
     * <p>
     * 支持SPEL表达式，可使用方法参数和变量：
     * <ul>
     *     <li>#args[0] - 第一个参数</li>
     *     <li>#userDTO.username - 对象属性</li>
     *     <li>#userId - 当前用户ID（自动注入）</li>
     * </ul>
     * 为空时默认使用：类名+方法名+用户ID
     *
     * @return SPEL表达式
     */
    String key() default "";

    /**
     * 锁过期时间（秒）
     * <p>
     * 防止死锁，业务执行时间不应超过此值。
     *
     * @return 过期时间，默认60秒
     */
    int expire() default 60;

    /**
     * 提示消息
     * <p>
     * 重复提交时返回的友好提示。
     *
     * @return 提示消息
     */
    String message() default "请求处理中，请勿重复提交";
}
