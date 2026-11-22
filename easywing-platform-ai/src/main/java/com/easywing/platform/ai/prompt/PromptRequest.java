package com.easywing.platform.ai.prompt;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建 / 更新 Prompt 的请求体。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public record PromptRequest(@NotBlank String title, @NotBlank String content, String category) {}
