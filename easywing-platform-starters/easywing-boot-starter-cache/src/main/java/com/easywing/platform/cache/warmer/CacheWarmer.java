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

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * 缓存预热器接口
 * <p>
 * 用于应用启动时预热缓存数据
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface CacheWarmer {

    /**
     * 获取预热器名称
     */
    String getName();

    /**
     * 执行缓存预热
     */
    void warmUp();

    /**
     * 获取预热顺序（越小越先执行）
     */
    default int getOrder() {
        return 0;
    }
}
