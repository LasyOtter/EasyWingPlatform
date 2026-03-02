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
package com.easywing.platform.data.annotation;

import java.lang.annotation.*;

/**
 * 忽略数据权限注解
 * <p>
 * 用于标记在方法或类上，表示该方法或类中的查询操作将跳过数据权限过滤。
 * 通常在管理员导出所有数据、系统级任务等场景使用。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreDataScope {
    /**
     * 忽略数据权限的原因说明
     *
     * @return 原因描述
     */
    String reason() default "";
}
