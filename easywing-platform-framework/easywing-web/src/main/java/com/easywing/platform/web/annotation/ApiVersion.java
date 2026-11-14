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
package com.easywing.platform.web.annotation;

import java.lang.annotation.*;

/**
 * API 版本注解
 * <p>
 * 用于标识 API 的版本信息，支持版本化的 REST API 设计
 *
 * <p>使用示例：
 * <pre>
 * {@code @RestController}
 * {@code @RequestMapping("/api/users")}
 * {@code @ApiVersion("v1")}
 * public class UserControllerV1 {
 *     // v1 版本的接口
 * }
 *
 * {@code @RestController}
 * {@code @RequestMapping("/api/users")}
 * {@code @ApiVersion("v2")}
 * public class UserControllerV2 {
 *     // v2 版本的接口
 * }
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API 版本号
     * <p>
     * 建议使用格式: "v1", "v2", "v1.1" 等
     *
     * @return 版本号
     */
    String value() default "v1";

    /**
     * 是否为默认版本
     * <p>
     * 如果为 true，则该版本在不指定版本时会被使用
     *
     * @return 是否为默认版本
     */
    boolean isDefault() default false;

    /**
     * 该版本是否已弃用
     * <p>
     * 如果为 true，会在响应头中添加弃用警告
     *
     * @return 是否已弃用
     */
    boolean deprecated() default false;

    /**
     * 弃用说明信息
     *
     * @return 弃用说明
     */
    String deprecationMessage() default "";

    /**
     * 推荐使用的替代版本
     *
     * @return 替代版本号
     */
    String replacedBy() default "";
}
