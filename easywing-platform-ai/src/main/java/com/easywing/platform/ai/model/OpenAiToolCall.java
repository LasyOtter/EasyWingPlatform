package com.easywing.platform.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Tool Call - 模型返回的工具调用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiToolCall {

    /**
     * 工具调用 ID
     */
    private String id;

    /**
     * 类型，通常是 "function"
     */
    private String type;

    /**
     * Function 调用信息
     */
    private FunctionCall function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        /**
         * 函数名称
         */
        private String name;

        /**
         * 函数参数（JSON 字符串）
         */
        private String arguments;
    }
}
