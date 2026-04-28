package com.easywing.platform.ai.tool;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AiTool {
    String getName();
    String getDescription();
    Mono<Map<String, Object>> execute(Map<String, Object> arguments);
}