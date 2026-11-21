package com.easywing.platform.ai.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具缓存键 - 用于缓存工具执行结果
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
public class ToolCacheKey {
    private String toolName;
    private Map<String, Object> arguments;
}
