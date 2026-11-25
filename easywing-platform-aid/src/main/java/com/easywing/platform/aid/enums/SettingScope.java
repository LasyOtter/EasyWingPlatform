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

import java.util.Arrays;
import lombok.Getter;

/**
 * 视觉设定作用域（AC-11：模板导出/导入）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Getter
public enum SettingScope {

    /** 项目级：仅服务于创建它的项目 */
    PROJECT("PROJECT"),
    /** 模板：可导出为 JSON 并被其它项目导入 */
    TEMPLATE("TEMPLATE");

    private final String code;

    SettingScope(String code) {
        this.code = code;
    }

    /**
     * 按 code 反查枚举。
     *
     * @param code 作用域码
     * @return 对应枚举
     * @throws IllegalArgumentException 未知作用域
     */
    public static SettingScope of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的视觉设定作用域: " + code));
    }
}
