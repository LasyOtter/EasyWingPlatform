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
package com.easywing.platform.aid.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作日志业务类型。
 * <p>
 * 与 system 模块的 {@code BusinessType} 取值对齐，便于后续把 AID 的操作日志
 * 汇入统一审计视图；但注解与切面是 aid 模块自有的一套——system 的 LogAspect
 * 依赖 SysOperLogService 与其专属注解，跨模块复用会引入反向依赖。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    /** 其他 */
    OTHER(0, "其他"),
    /** 新增 */
    INSERT(1, "新增"),
    /** 修改 */
    UPDATE(2, "修改"),
    /** 删除 */
    DELETE(3, "删除"),
    /** 导出 */
    EXPORT(5, "导出"),
    /** 导入 */
    IMPORT(6, "导入"),
    /** 生成（提交 AI 任务） */
    GENERATE(7, "生成");

    private final int code;
    private final String description;
}
