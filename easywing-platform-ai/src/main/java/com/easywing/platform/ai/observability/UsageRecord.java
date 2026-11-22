package com.easywing.platform.ai.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次模型调用的用量与估算成本记录。
 * <p>
 * 由 {@code AiProviderRegistry} 在每次 chat / stream 调用完成后写入，
 * 供用户维度的用量查询与管理员维度的成本审计使用。
 * 进程内存储，多实例部署时各实例独立，仅作为可观测性数据而非计费依据。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRecord {

    private String id;

    /** 调用方用户标识，来自 JWT 主体；无法归属时为 {@code null}。 */
    private String userId;

    /** 调用方租户标识，来自 JWT 的 {@code tenant_id} claim。 */
    private String tenantId;

    private String provider;

    private String model;

    private int promptTokens;

    private int completionTokens;

    private int totalTokens;

    /** 按模型目录中的单价估算的美元成本，缺定价时为 0。 */
    private double estimatedCostUsd;

    private long timestamp;
}
