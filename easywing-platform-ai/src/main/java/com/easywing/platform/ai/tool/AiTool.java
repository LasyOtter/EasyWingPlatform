package com.easywing.platform.ai.tool;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface AiTool {
    String getName();

    String getDescription();

    Mono<Map<String, Object>> execute(Map<String, Object> arguments);

    /**
     * 获取工具参数的 JSON Schema
     * @return JSON Schema 字符串，如果不需要验证可返回 null
     */
    default String getParametersSchema() {
        return null;
    }
}
