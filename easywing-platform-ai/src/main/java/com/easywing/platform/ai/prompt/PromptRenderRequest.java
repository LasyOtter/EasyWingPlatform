package com.easywing.platform.ai.prompt;

import java.util.Map;

/**
 * Prompt 渲染请求体：变量名 -> 取值。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public record PromptRenderRequest(Map<String, String> variables) {}
