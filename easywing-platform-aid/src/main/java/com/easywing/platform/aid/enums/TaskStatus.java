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
 * 生成任务状态（PRD FR-9 / FR-10）。
 * <p>
 * {@link #isTerminal()} 供调度器判断是否还需继续轮询/重投——终态任务不再占用队列位。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Getter
public enum TaskStatus {

    /** 已入队，等待派发 */
    PENDING("PENDING"),
    /** 执行中 */
    RUNNING("RUNNING"),
    /** 成功 */
    COMPLETED("COMPLETED"),
    /** 失败（可能仍可重试） */
    FAILED("FAILED"),
    /** 已取消：用户主动放弃或被新版本快照淘汰 */
    CANCELED("CANCELED");

    private final String code;

    TaskStatus(String code) {
        this.code = code;
    }

    /**
     * 是否为终态。
     *
     * @return 成功/失败/取消返回 true
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    /**
     * 按 code 反查枚举。
     *
     * @param code 状态码
     * @return 对应枚举
     * @throws IllegalArgumentException 未知状态码
     */
    public static TaskStatus of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的任务状态: " + code));
    }
}
