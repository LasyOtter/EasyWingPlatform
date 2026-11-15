package com.easywing.platform.ai.skill.impl;

import com.easywing.platform.ai.skill.SkillContext;
import com.easywing.platform.ai.skill.SkillExecutor;
import com.easywing.platform.ai.skill.SkillResult;
import com.easywing.platform.ai.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本处理技能 - 对文本进行各种处理操作
 */
@Slf4j
@Component
public class TextProcessingSkill implements SkillExecutor {

    private final ToolExecutor toolExecutor;

    public TextProcessingSkill(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Mono<SkillResult> execute(SkillContext context) {
        log.info("Executing text processing skill");

        String text = (String) context.getParameters().get("text");
        String operation = (String) context.getParameters().get("operation");

        if (text == null || text.isEmpty()) {
            return Mono.just(SkillResult.failure("文本内容为空"));
        }

        if (operation == null) {
            return Mono.just(SkillResult.failure("未指定操作类型"));
        }

        Map<String, Object> toolArgs = new HashMap<>();
        toolArgs.put("text", text);
        toolArgs.put("operation", operation);

        // 添加操作特定参数
        context.getParameters().forEach((key, value) -> {
            if (!key.equals("text") && !key.equals("operation")) {
                toolArgs.put(key, value);
            }
        });

        return toolExecutor.executeTool("string_manipulation", toolArgs)
                .map(result -> {
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        return SkillResult.success(
                                result.get("result"),
                                "文本处理完成: " + operation
                        );
                    } else {
                        return SkillResult.failure((String) result.get("error"));
                    }
                })
                .doOnSuccess(r -> {
                    if (r.isSuccess()) {
                        r.setToolsUsed(List.of("string_manipulation"));
                    }
                });
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "text": {
                      "type": "string",
                      "description": "要处理的文本"
                    },
                    "operation": {
                      "type": "string",
                      "enum": ["uppercase", "lowercase", "reverse", "length", "trim", "replace", "substring", "contains", "split", "similarity"],
                      "description": "操作类型"
                    },
                    "from": {
                      "type": "string",
                      "description": "要替换的字符串（用于 replace）"
                    },
                    "to": {
                      "type": "string",
                      "description": "替换后的字符串（用于 replace）"
                    },
                    "start": {
                      "type": "integer",
                      "description": "起始位置（用于 substring）"
                    },
                    "end": {
                      "type": "integer",
                      "description": "结束位置（用于 substring）"
                    },
                    "search": {
                      "type": "string",
                      "description": "搜索文本（用于 contains）"
                    },
                    "delimiter": {
                      "type": "string",
                      "description": "分隔符（用于 split）"
                    },
                    "compareText": {
                      "type": "string",
                      "description": "比较文本（用于 similarity）"
                    }
                  },
                  "required": ["text", "operation"]
                }
                """;
    }
}
