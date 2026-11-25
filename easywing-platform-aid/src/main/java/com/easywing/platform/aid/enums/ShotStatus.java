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
 * 分镜状态。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Getter
public enum ShotStatus {

    /** 草稿：已录入但尚未提交生成 */
    DRAFT("DRAFT"),
    /** 生成中 */
    GENERATING("GENERATING"),
    /** 已完成：图片/视频均已产出 */
    COMPLETED("COMPLETED"),
    /** 失败 */
    FAILED("FAILED");

    private final String code;

    ShotStatus(String code) {
        this.code = code;
    }

    /**
     * 按 code 反查枚举。
     *
     * @param code 状态码
     * @return 对应枚举
     * @throws IllegalArgumentException 未知状态码
     */
    public static ShotStatus of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的分镜状态: " + code));
    }
}
