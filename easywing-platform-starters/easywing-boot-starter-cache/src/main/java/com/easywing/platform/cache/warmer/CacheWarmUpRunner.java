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
package com.easywing.platform.cache.warmer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 缓存预热启动器
 * <p>
 * 应用启动完成后自动执行缓存预热
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "easywing.cache", name = "warmup-enabled", havingValue = "true")
public class CacheWarmUpRunner implements ApplicationRunner {

    private final List<CacheWarmer> warmers;

    @Override
    public void run(ApplicationArguments args) {
        if (warmers == null || warmers.isEmpty()) {
            log.debug("No cache warmers registered");
            return;
        }

        log.info("Starting cache warm-up with {} warmers", warmers.size());
        
        // 按顺序执行预热
        warmers.stream()
                .sorted(Comparator.comparingInt(CacheWarmer::getOrder))
                .forEach(warmer -> {
                    try {
                        log.info("Running cache warmer: {}", warmer.getName());
                        long start = System.currentTimeMillis();
                        warmer.warmUp();
                        long elapsed = System.currentTimeMillis() - start;
                        log.info("Cache warmer {} completed in {}ms", warmer.getName(), elapsed);
                    } catch (Exception e) {
                        log.error("Cache warmer {} failed", warmer.getName(), e);
                    }
                });
        
        log.info("Cache warm-up completed");
    }
}
