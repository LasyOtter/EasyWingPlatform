package com.easywing.platform.ai.starter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AI Starter 自动装配可独立加载，且不与 AI 服务模块的同名类冲突。
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "easywing.ai.chat-model.enabled=false",
                    "easywing.ai.embedding-model.enabled=false",
                    "easywing.ai.rag.enabled=false",
                    "easywing.ai.agent.enabled=false"
            );

    @Test
    void autoConfigurationLoads() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration
    @Import(AiAutoConfiguration.class)
    static class TestConfig {
    }
}
