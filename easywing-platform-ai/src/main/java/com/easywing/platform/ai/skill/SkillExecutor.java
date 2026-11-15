package com.easywing.platform.ai.skill;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 技能执行器接口 - 定义技能的执行行为
 */
public interface SkillExecutor {

    /**
     * 执行技能
     *
     * @param context 执行上下文，包含输入参数和环境信息
     * @return 技能执行结果
     */
    Mono<SkillResult> execute(SkillContext context);

    /**
     * 验证输入参数
     *
     * @param parameters 输入参数
     * @return 验证是否通过
     */
    default boolean validateParameters(Map<String, Object> parameters) {
        return true;
    }

    /**
     * 获取技能的输入参数 Schema
     *
     * @return JSON Schema 字符串
     */
    default String getParametersSchema() {
        return "{}";
    }
}
