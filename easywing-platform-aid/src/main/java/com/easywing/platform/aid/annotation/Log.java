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
package com.easywing.platform.aid.annotation;

import com.easywing.platform.aid.enums.BusinessType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * <p>由 aid 模块自己的切面处理，只写应用日志，不落库（M1 阶段未建操作日志表）。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 功能名称 */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数 */
    boolean isSaveRequestData() default true;

    /** 需要排除的敏感参数名 */
    String[] excludeParamNames() default {};
}
