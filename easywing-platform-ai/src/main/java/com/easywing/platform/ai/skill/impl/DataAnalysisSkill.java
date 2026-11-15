package com.easywing.platform.ai.skill.impl;

import com.easywing.platform.ai.skill.SkillContext;
import com.easywing.platform.ai.skill.SkillExecutor;
import com.easywing.platform.ai.skill.SkillResult;
import com.easywing.platform.ai.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 数据分析技能 - 对数据进行统计分析
 */
@Slf4j
@Component
public class DataAnalysisSkill implements SkillExecutor {

    private final ToolExecutor toolExecutor;

    public DataAnalysisSkill(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Mono<SkillResult> execute(SkillContext context) {
        log.info("Executing data analysis skill");

        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) context.getParameters().get("data");
        String analysisType = (String) context.getParameters().getOrDefault("type", "statistics");

        if (data == null || data.isEmpty()) {
            return Mono.just(SkillResult.failure("数据为空"));
        }

        Map<String, Object> toolArgs = new HashMap<>();
        toolArgs.put("operation", analysisType);
        toolArgs.put("data", data);

        // 添加其他参数
        if (context.getParameters().containsKey("bins")) {
            toolArgs.put("bins", context.getParameters().get("bins"));
        }
        if (context.getParameters().containsKey("threshold")) {
            toolArgs.put("threshold", context.getParameters().get("threshold"));
        }
        if (context.getParameters().containsKey("condition")) {
            toolArgs.put("condition", context.getParameters().get("condition"));
        }
        if (context.getParameters().containsKey("order")) {
            toolArgs.put("order", context.getParameters().get("order"));
        }

        return toolExecutor.executeTool("data_analysis", toolArgs)
                .map(result -> {
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        return SkillResult.success(
                                result.get("result"),
                                "数据分析完成"
                        );
                    } else {
                        return SkillResult.failure((String) result.get("error"));
                    }
                })
                .doOnSuccess(r -> {
                    if (r.isSuccess()) {
                        r.setToolsUsed(List.of("data_analysis"));
                    }
                });
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "data": {
                      "type": "array",
                      "description": "要分析的数值数据数组"
                    },
                    "type": {
                      "type": "string",
                      "enum": ["statistics", "sort", "filter", "group"],
                      "description": "分析类型"
                    },
                    "bins": {
                      "type": "integer",
                      "description": "分组数量（用于 group 操作）"
                    },
                    "threshold": {
                      "type": "number",
                      "description": "过滤阈值（用于 filter 操作）"
                    },
                    "condition": {
                      "type": "string",
                      "description": "过滤条件：gt, gte, lt, lte, eq"
                    },
                    "order": {
                      "type": "string",
                      "enum": ["asc", "desc"],
                      "description": "排序顺序"
                    }
                  },
                  "required": ["data"]
                }
                """;
    }
}
