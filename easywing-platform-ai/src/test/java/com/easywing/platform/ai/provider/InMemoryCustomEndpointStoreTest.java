package com.easywing.platform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.config.AiProperties.CustomEndpointConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link InMemoryCustomEndpointStore} 与注册中心持久化行为测试（PRD 4.3）。
 * <p>
 * 覆盖存储读写语义，以及「运行时新增/删除端点写入存储、进程重启后由存储恢复」的契约。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class InMemoryCustomEndpointStoreTest {

    private InMemoryCustomEndpointStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCustomEndpointStore();
    }

    @Test
    void loadAllReturnsEmptyWhenNothingStored() {
        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void saveThenLoadRoundTripsConfig() {
        store.save("my-llm", endpoint("https://llm.internal/v1", "sk-test"));

        Map<String, CustomEndpointConfig> loaded = store.loadAll();

        assertThat(loaded).containsOnlyKeys("my-llm");
        assertThat(loaded.get("my-llm").getBaseUrl()).isEqualTo("https://llm.internal/v1");
        assertThat(loaded.get("my-llm").getApiKey()).isEqualTo("sk-test");
        assertThat(loaded.get("my-llm").getModels()).containsExactly("gpt-4o");
    }

    @Test
    void saveOverwritesSameId() {
        store.save("my-llm", endpoint("https://old/v1", "sk-old"));
        store.save("my-llm", endpoint("https://new/v1", "sk-new"));

        assertThat(store.loadAll()).hasSize(1);
        assertThat(store.loadAll().get("my-llm").getBaseUrl()).isEqualTo("https://new/v1");
    }

    @Test
    void deleteRemovesOnlyTargetId() {
        store.save("a", endpoint("https://a/v1", "sk-a"));
        store.save("b", endpoint("https://b/v1", "sk-b"));

        store.delete("a");

        assertThat(store.loadAll()).containsOnlyKeys("b");
    }

    @Test
    void deleteIsNoOpForMissingId() {
        store.delete("missing");

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void saveIgnoresNullIdOrConfig() {
        store.save(null, endpoint("https://a/v1", "sk-a"));
        store.save("a", null);

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void deleteIgnoresNullId() {
        store.delete(null);

        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void registryRestoresPersistedEndpointsOnConstruction() {
        store.save("restored", endpoint("https://restored/v1", "sk-restored"));

        AiProviderRegistry registry = newRegistry(store);

        Optional<AiProvider> provider = registry.getProvider("custom-restored");
        assertThat(provider).isPresent();
        assertThat(provider.get().getProviderName()).isEqualTo("custom-restored");
        assertThat(provider.get().getSupportedModels()).isNotEmpty();
    }

    @Test
    void registrySkipsDisabledPersistedEndpoints() {
        CustomEndpointConfig disabled = endpoint("https://disabled/v1", "sk-disabled");
        disabled.setEnabled(false);
        store.save("disabled", disabled);

        AiProviderRegistry registry = newRegistry(store);

        assertThat(registry.getProvider("custom-disabled")).isEmpty();
    }

    @Test
    void registryPersistsRuntimeRegistration() {
        AiProviderRegistry registry = newRegistry(store);

        registry.registerCustomEndpoint("added", endpoint("https://added/v1", "sk-added"));

        assertThat(store.loadAll()).containsKey("added");
        assertThat(registry.getProvider("custom-added")).isPresent();
    }

    @Test
    void registryPersistsRuntimeRemoval() {
        AiProviderRegistry registry = newRegistry(store);
        registry.registerCustomEndpoint("removed", endpoint("https://removed/v1", "sk-removed"));

        registry.removeCustomEndpoint("removed");

        assertThat(store.loadAll()).doesNotContainKey("removed");
        assertThat(registry.getProvider("custom-removed")).isEmpty();
    }

    private AiProviderRegistry newRegistry(CustomEndpointStore store) {
        AiProperties properties = new AiProperties();
        properties.setProviders(new HashMap<>());
        properties.setCustomEndpoints(new HashMap<>());
        return new AiProviderRegistry(List.of(), properties, WebClient.builder(), null, store);
    }

    private CustomEndpointConfig endpoint(String baseUrl, String apiKey) {
        CustomEndpointConfig config = new CustomEndpointConfig();
        config.setEnabled(true);
        config.setName("自定义端点");
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setModels(List.of("gpt-4o"));
        return config;
    }
}
