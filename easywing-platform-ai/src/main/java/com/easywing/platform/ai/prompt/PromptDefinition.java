package com.easywing.platform.ai.prompt;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt 库条目：用户可保存可复用的提示词模板，支持 {@code {{变量}}} 占位符。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptDefinition {

    private String id;

    private String userId;

    private String tenantId;

    private String title;

    /** 模板正文，可包含 {{变量名}} 占位符 */
    private String content;

    @Builder.Default
    private String category = "default";

    /** 从正文解析出的变量名（去重、字典序） */
    @Builder.Default
    private List<String> variables = new ArrayList<>();

    private long createdAt;

    private long updatedAt;
}
