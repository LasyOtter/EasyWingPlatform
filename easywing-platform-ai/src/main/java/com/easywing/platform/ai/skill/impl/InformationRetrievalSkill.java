package com.easywing.platform.ai.skill.impl;

import com.easywing.platform.ai.skill.SkillContext;
import com.easywing.platform.ai.skill.SkillExecutor;
import com.easywing.platform.ai.skill.SkillResult;
import com.easywing.platform.ai.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 信息检索技能 - 从多个来源检索和聚合信息
 */
@Slf4j
@Component
public class InformationRetrievalSkill implements SkillExecutor {

    private final ToolExecutor toolExecutor;

    public InformationRetrievalSkill(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Mono<SkillResult> execute(SkillContext context) {
        log.info("Executing information retrieval skill");

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) context.getParameters().get("urls");
        String method = (String) context.getParameters().getOrDefault("method", "GET");

        if (urls == null || urls.isEmpty()) {
            return Mono.just(SkillResult.failure("未提供 URL"));
        }

        List<String> toolsUsed = new ArrayList<>();

        // 并行请求多个 URL
        return Flux.fromIterable(urls)
                .flatMap(url -> {
                    Map<String, Object> toolArgs = new HashMap<>();
                    toolArgs.put("url", url);
                    toolArgs.put("method", method);

                    return toolExecutor.executeTool("http_request", toolArgs)
                            .doOnSuccess(r -> toolsUsed.add("http_request"));
                })
                .collectList()
                .map(results -> {
                    List<Map<String, Object>> successResults = new ArrayList<>();
                    List<String> errors = new ArrayList<>();

                    for (Map<String, Object> result : results) {
                        if (Boolean.TRUE.equals(result.get("success"))) {
                            successResults.add(result);
                        } else {
                            errors.add((String) result.get("error"));
                        }
                    }

                    Map<String, Object> aggregatedResult = new HashMap<>();
                    aggregatedResult.put("totalRequests", urls.size());
                    aggregatedResult.put("successCount", successResults.size());
                    aggregatedResult.put("failureCount", errors.size());
                    aggregatedResult.put("results", successResults);

                    if (!errors.isEmpty()) {
                        aggregatedResult.put("errors", errors);
                    }

                    String message = String.format(
                            "检索完成: %d/%d 成功",
                            successResults.size(),
                            urls.size()
                    );

                    SkillResult skillResult = SkillResult.success(aggregatedResult, message);
                    skillResult.setToolsUsed(toolsUsed);
                    return skillResult;
                })
                .onErrorResume(e -> {
                    log.error("Information retrieval failed", e);
                    return Mono.just(SkillResult.failure("信息检索失败: " + e.getMessage()));
                });
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "urls": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "description": "要检索的 URL 列表"
                    },
                    "method": {
                      "type": "string",
                      "enum": ["GET", "POST"],
                      "description": "HTTP 方法",
                      "default": "GET"
                    }
                  },
                  "required": ["urls"]
                }
                """;
    }
}
