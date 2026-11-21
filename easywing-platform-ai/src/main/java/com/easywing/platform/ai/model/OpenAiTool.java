package com.easywing.platform.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Tool 定义 - 用于 Function Calling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiTool {

    /**
     * 工具类型，目前只支持 function
     */
    @Builder.Default
    private String type = "function";

    /**
     * Function 定义
     */
    private FunctionDefinition function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinition {
        /**
         * 函数名称
         */
        private String name;

        /**
         * 函数描述
         */
        private String description;

        /**
         * 参数 Schema (JSON Schema 格式)
         */
        private Object parameters;
    }
}
