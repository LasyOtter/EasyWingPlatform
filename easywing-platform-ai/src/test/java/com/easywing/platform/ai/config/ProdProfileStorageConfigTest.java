package com.easywing.platform.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 校验生产 profile 把多实例共享状态切到 Redis（PRD 5.3 无状态后端、4.7 租户与共享隔离）。
 *
 * <p>直接加载 application-prod.yml 并绑定键位，避免 YAML 键名拼写错误导致配置静默失效。
 */
class ProdProfileStorageConfigTest {

    private static final String[] SHARED_STORES = {
        "easywing.ai.session.storage-type",
        "easywing.ai.skill.storage-type",
        "easywing.ai.usage.storage-type",
        "easywing.ai.trace.storage-type",
        "easywing.ai.agent-def.storage-type",
        "easywing.ai.prompt.storage-type",
        "easywing.ai.share.storage-type",
        "easywing.ai.workspace.storage-type",
    };

    private final Binder prodBinder;

    ProdProfileStorageConfigTest() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        Iterable<ConfigurationPropertySource> propertySources = ConfigurationPropertySources.from(sources);
        this.prodBinder = new Binder(propertySources);
    }

    @Test
    void sharedStateStoresBindToRedis() {
        for (String key : SHARED_STORES) {
            BindResult<String> result = prodBinder.bind(key, Bindable.of(String.class));
            assertThat(result.isBound()).as("生产 profile 应将 %s 绑定为 redis", key).isTrue();
            assertThat(result.get()).isEqualTo("redis");
        }
    }

    @Test
    void customEndpointStoreNotOverriddenByProd() {
        BindResult<String> result =
                prodBinder.bind("easywing.ai.custom-endpoint-store.storage-type", Bindable.of(String.class));
        assertThat(result.isBound())
                .as("Redis 模式会把 apiKey 明文写入 Redis，生产 profile 不应默认开启")
                .isFalse();
    }
}
