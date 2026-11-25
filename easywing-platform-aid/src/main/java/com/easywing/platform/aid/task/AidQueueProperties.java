/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.aid.task;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AID 任务队列配置（{@code easywing.aid.queue.*}）。
 *
 * <p>默认值按 M0 实测校准：</p>
 * <ul>
 *   <li>{@code maxLen=100000} — Stream 近似裁剪阈值，防止无限增长。</li>
 *   <li>{@code batchSize=32} — 单次 XREADGROUP 拉取条数；实测 COUNT=1000 读取约 1ms，
 *       过小会放大 RTT 影响，过大则单批处理阻塞时间长。</li>
 *   <li>{@code reclaimMinIdleMs=60000} — 与 FR-10.4「重试 3 次 + 退避」配合，
 *       需大于最长重试间隔，避免同一任务被两个实例同时抢活。</li>
 *   <li>{@code dedupTtlSeconds=86400} — 幂等窗口，需覆盖全部重试周期。</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.aid.queue")
public class AidQueueProperties {

    /** 队列实现：redis-stream（默认）或 local。 */
    private String type = "redis-stream";

    /** Redis Stream key。 */
    private String streamKey = "aid:tasks";

    /** 消费组名称。 */
    private String group = "aid-workers";

    /** Stream 最大长度（近似裁剪，~ 表示流式裁剪而非精确截断）。 */
    private long maxLen = 100_000L;

    /** 派发线程数（工作窃取式竞争）。 */
    private int consumerThreads = 2;

    /** 单次拉取条数。 */
    private int batchSize = 32;

    /** 拉取阻塞时长（毫秒），-1 表示一直阻塞。 */
    private long blockTimeoutMs = 2_000L;

    /** 派发循环轮询间隔（毫秒），仅在非阻塞读后生效。 */
    private long pollIntervalMs = 200L;

    /** 挂起消息回收任务执行间隔（毫秒）。 */
    private long reclaimIntervalMs = 30_000L;

    /** 消息未确认多久后可被其它实例接管（毫秒）。 */
    private long reclaimMinIdleMs = 60_000L;

    /** 幂等去重 key 前缀。 */
    private String dedupKeyPrefix = "aid:task:dedup:";

    /** 幂等去重 TTL（秒）。 */
    private long dedupTtlSeconds = 86_400L;

    /** 是否启用队列（false 时仅使用本地模式，对应 NFR-9 降级）。 */
    private boolean enabled = true;
}
