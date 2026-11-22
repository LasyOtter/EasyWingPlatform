package com.easywing.platform.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.tool.impl.CalculatorTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Agent 定义工具白名单的单元测试（PRD 4.2）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class ToolWhitelistTests {

    private static ToolExecutor newExecutor() {
        ToolExecutor executor =
                new ToolExecutor(new ToolRegistry(), new ToolParameterValidator(new ObjectMapper()), cacheProvider());
        executor.registerTool(new CalculatorTool());
        return executor;
    }

    private static org.springframework.beans.factory.ObjectProvider<ToolExecutionCache> cacheProvider() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public ToolExecutionCache getObject(Object... args) {
                return null;
            }

            @Override
            public ToolExecutionCache getIfAvailable() {
                return null;
            }

            @Override
            public ToolExecutionCache getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void filtersDefinitionsByAllowedList() {
        ToolExecutor executor = newExecutor();
        List<ToolDefinition> filtered =
                ToolExecutor.filterByAllowed(executor.getAvailableTools(), List.of("calculator"));

        assertThat(filtered).extracting(ToolDefinition::getName).containsExactly("calculator");
    }

    @Test
    void emptyAllowedListMeansUnrestricted() {
        ToolExecutor executor = newExecutor();

        assertThat(ToolExecutor.filterByAllowed(executor.getAvailableTools(), null))
                .isEqualTo(executor.getAvailableTools());
        assertThat(ToolExecutor.filterByAllowed(executor.getAvailableTools(), List.of()))
                .isEqualTo(executor.getAvailableTools());
    }

    @Test
    void rejectsToolOutsideWhitelist() {
        ToolExecutor executor = newExecutor();

        Map<String, Object> result = executor.executeTool(
                        "calculator", Map.of("expression", "1+1"), List.of("other-tool"))
                .block();

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error").toString()).contains("允许列表");
    }

    @Test
    void allowsWhitelistedTool() {
        ToolExecutor executor = newExecutor();

        Map<String, Object> result = executor.executeTool(
                        "calculator", Map.of("expression", "2 + 2"), List.of("calculator"))
                .block();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("result", 4.0);
    }

    @Test
    void whitelistMatchIsCaseInsensitiveAndTrimmed() {
        ToolExecutor executor = newExecutor();

        Map<String, Object> result = executor.executeTool(
                        "calculator", Map.of("expression", "3 * 3"), List.of("  CALCULATOR  "))
                .block();

        assertThat(result).containsEntry("success", true);
    }
}
