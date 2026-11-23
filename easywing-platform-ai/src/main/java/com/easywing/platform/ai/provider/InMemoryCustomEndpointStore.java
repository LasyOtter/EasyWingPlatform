package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties.CustomEndpointConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Custom Endpoint 的内存存储（默认实现）。
 * <p>
 * 单实例生效，重启或横向扩容会丢失/不一致；需要持久化与多实例共享时应配置
 * {@code easywing.ai.custom-endpoint-store.storage-type=redis}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "easywing.ai.custom-endpoint-store",
        name = "storage-type",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryCustomEndpointStore implements CustomEndpointStore {

    private final Map<String, CustomEndpointConfig> endpoints = new ConcurrentHashMap<>();

    public InMemoryCustomEndpointStore() {
        log.warn("Custom Endpoint 使用内存存储：运行时新增/删除的端点在进程重启后丢失且多实例不共享，"
                + "如需持久化请配置 easywing.ai.custom-endpoint-store.storage-type=redis");
    }

    @Override
    public Map<String, CustomEndpointConfig> loadAll() {
        return new LinkedHashMap<>(endpoints);
    }

    @Override
    public void save(String id, CustomEndpointConfig config) {
        if (id == null || config == null) {
            return;
        }
        endpoints.put(id, config);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            endpoints.remove(id);
        }
    }
}
