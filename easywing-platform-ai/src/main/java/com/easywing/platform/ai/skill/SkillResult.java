package com.easywing.platform.ai.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 技能执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 结果数据
     */
    private Object data;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行时间（毫秒）
     */
    private long executionTimeMs;

    /**
     * 使用的工具列表
     */
    private java.util.List<String> toolsUsed;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建成功结果
     */
    public static SkillResult success(Object data, String message) {
        return SkillResult.builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static SkillResult failure(String error) {
        return SkillResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
