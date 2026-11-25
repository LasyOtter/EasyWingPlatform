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
 * 资产类型（PRD FR-6）。
 * <p>决定参考图在提示词中的语义前缀，进而影响 Provider 侧的参考图权重分配。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Getter
public enum AssetType {

    /** 角色 */
    CHARACTER("CHARACTER"),
    /** 场景 */
    SCENE("SCENE"),
    /** 道具 */
    PROP("PROP"),
    /** 风格资产（如画风参考） */
    STYLE("STYLE");

    private final String code;

    AssetType(String code) {
        this.code = code;
    }

    /**
     * 按 code 反查枚举。
     *
     * @param code 类型码
     * @return 对应枚举
     * @throws IllegalArgumentException 未知类型码
     */
    public static AssetType of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的资产类型: " + code));
    }
}
