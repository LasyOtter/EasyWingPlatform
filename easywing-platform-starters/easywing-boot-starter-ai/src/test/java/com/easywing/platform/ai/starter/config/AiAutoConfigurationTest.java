package com.easywing.platform.ai.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.starter.rag.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 验证 AI Starter 自动装配可独立加载，且不与 AI 服务模块的同名类冲突。
 */
class AiAutoConfigurationTest {

    private static final String[] DISABLED = {
        "easywing.ai.enabled=false",
        "easywing.ai.chat-model.enabled=false",
        "easywing.ai.embedding-model.enabled=false",
        "easywing.ai.embedding-model.local.enabled=false",
        "easywing.ai.rag.enabled=false",
        "easywing.ai.agent.enabled=false"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(DISABLED);

    @Test
    void autoConfigurationLoads() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * RAG 基础设施（向量库 / 文档切分 / RagService）在未配置任何 EmbeddingModel 时仍可装配，
     * 不得因缺少 Embedding 候选 Bean 导致上下文启动失败。
     */
    @Test
    void ragInfrastructureLoadsWithoutEmbeddingModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "easywing.ai.chat-model.enabled=false",
                        "easywing.ai.embedding-model.enabled=false",
                        "easywing.ai.agent.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RagService.class);
                });
    }

    @Configuration
    @Import(AiAutoConfiguration.class)
    static class TestConfig {}
}
