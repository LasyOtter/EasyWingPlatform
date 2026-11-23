package com.easywing.platform.ai.provider;

import com.easywing.platform.ai.config.AiProperties.CustomEndpointConfig;
import java.util.Map;

/**
 * Custom Endpoint 运行时存储。
 * <p>
 * 运行时通过 API 新增/删除的端点写入该存储，使变更在进程重启后仍可恢复、在多实例间共享。
 * 实现由 {@code easywing.ai.custom-endpoint-store.storage-type} 选择：
 * {@code memory}（默认，仅当前进程）或 {@code redis}（持久化并跨实例共享）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface CustomEndpointStore {

    /**
     * 读取全部已持久化的端点。
     *
     * @return 端点 id 到配置的映射，无数据时返回空 Map
     */
    Map<String, CustomEndpointConfig> loadAll();

    /**
     * 保存（新增或覆盖）一个端点。
     *
     * @param id     端点 id
     * @param config 端点配置
     */
    void save(String id, CustomEndpointConfig config);

    /**
     * 删除一个端点。
     *
     * @param id 端点 id
     */
    void delete(String id);
}
