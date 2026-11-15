package com.easywing.platform.ai.tool;

import com.easywing.platform.ai.tool.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工具自动配置 - 注册所有内置工具
 */
@Slf4j
@Configuration
public class ToolAutoConfiguration {

    /**
     * 工具注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    /**
     * 初始化所有内置工具
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolInitializer toolInitializer(
            ToolExecutor toolExecutor,
            List<AiTool> tools) {

        log.info("Initializing tools, found {} tool beans", tools.size());

        // 注册所有 Spring 管理的工具 Bean
        toolExecutor.registerTools(tools);

        return new ToolInitializer(toolExecutor);
    }

    /**
     * 工具初始化器
     */
    public static class ToolInitializer {
        private final ToolExecutor toolExecutor;

        public ToolInitializer(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            log.info("Tool initialization completed, {} tools available",
                    toolExecutor.getAvailableTools().size());
        }

        public ToolExecutor getToolExecutor() {
            return toolExecutor;
        }
    }
}
