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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AID 任务队列自动配置。
 * <p>
 * 按 {@code easywing.aid.queue.type} 装配：
 * </p>
 * <ul>
 *   <li>{@code redis-stream}（默认，需 {@link StringRedisTemplate} 可用）</li>
 *   <li>{@code local}（无 Redis 环境的降级形态，对应 NFR-9）</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(AidQueueProperties.class)
public class TaskQueueAutoConfiguration {

    /**
     * Redis Stream 实现。
     * <p>仅在存在 {@link StringRedisTemplate} 时生效；缺少 Redis Bean 不会导致上下文启动失败，
     * 而是回退到 {@link LocalTaskQueue}——这正是 FR-10.9 要求的「关闭 MQ 后核心链路不中断」。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TaskQueue.class)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(
            prefix = "easywing.aid.queue",
            name = "type",
            havingValue = "redis-stream",
            matchIfMissing = true)
    public TaskQueue redisStreamTaskQueue(StringRedisTemplate redisTemplate, AidQueueProperties properties) {
        log.info("装配 Redis Stream 任务队列: stream={}", properties.getStreamKey());
        return new RedisStreamTaskQueue(redisTemplate, properties);
    }

    /**
     * 本地队列实现（降级）。
     */
    @Bean
    @ConditionalOnMissingBean(TaskQueue.class)
    @ConditionalOnProperty(prefix = "easywing.aid.queue", name = "type", havingValue = "local")
    public TaskQueue localTaskQueue() {
        log.warn("装配本地任务队列（降级模式，不跨实例、不持久化）：生产环境请改用 redis-stream");
        return new LocalTaskQueue();
    }

    /** 兜底：既没有 Redis Bean 也未显式指定 local 时，仍然提供可用的队列。 */
    @Bean
    @ConditionalOnMissingBean(TaskQueue.class)
    public TaskQueue fallbackTaskQueue(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("装配 Redis Stream 任务队列（fallback）");
            return new RedisStreamTaskQueue(redisTemplate, new AidQueueProperties());
        }
        log.warn("未检测到 StringRedisTemplate，装配本地任务队列（降级模式）");
        return new LocalTaskQueue();
    }
}
