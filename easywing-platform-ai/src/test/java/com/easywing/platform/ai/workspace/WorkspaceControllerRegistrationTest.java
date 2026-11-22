package com.easywing.platform.ai.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link WorkspaceController} 装配条件测试：默认与显式关闭时不注册，显式开启时注册。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class WorkspaceControllerRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void shouldNotRegisterWhenDisabled() {
        runner.withUserConfiguration(ControllerConfig.class)
                .withPropertyValues("easywing.ai.workspace.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WorkspaceController.class));
    }

    @Test
    void shouldNotRegisterWhenPropertyMissing() {
        runner.withUserConfiguration(ControllerConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(WorkspaceController.class));
    }

    @Test
    void shouldRegisterWhenEnabled() {
        runner.withUserConfiguration(ControllerConfig.class)
                .withBean(WorkspaceService.class, () -> Mockito.mock(WorkspaceService.class))
                .withPropertyValues("easywing.ai.workspace.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(WorkspaceController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(WorkspaceController.class)
    static class ControllerConfig {}
}
