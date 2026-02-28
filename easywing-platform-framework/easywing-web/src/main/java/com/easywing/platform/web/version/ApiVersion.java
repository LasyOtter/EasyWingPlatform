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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API版本控制注解
 * <p>
 * 用于标记Controller或方法的API版本，支持路径版本控制。
 * 配合 {@link ApiVersionConfig} 使用，自动添加版本前缀。
 * <p>
 * 示例：
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/users")
 * &#64;ApiVersion("v1")
 * public class UserController {
 *     // 访问路径: /api/v1/users
 * }
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {

    /**
     * API版本号
     *
     * @return 版本号，默认为 "v1"
     */
    String value() default "v1";
}
