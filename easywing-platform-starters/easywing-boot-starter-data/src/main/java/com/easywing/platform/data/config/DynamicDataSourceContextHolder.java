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
package com.easywing.platform.data.config;

/**
 * 动态数据源上下文持有者
 * <p>
 * 用于在当前线程中设置和获取数据源key
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class DynamicDataSourceContextHolder {

    private static final ThreadLocal<String> DATA_SOURCE_KEY = new ThreadLocal<>();

    /**
     * 主库（写库）标识
     */
    public static final String MASTER = "master";

    /**
     * 从库（读库）标识
     */
    public static final String SLAVE = "slave";

    private DynamicDataSourceContextHolder() {
    }

    /**
     * 设置当前线程使用的数据源key
     *
     * @param key 数据源key
     */
    public static void setDataSourceKey(String key) {
        DATA_SOURCE_KEY.set(key);
    }

    /**
     * 获取当前线程使用的数据源key
     *
     * @return 数据源key
     */
    public static String getDataSourceKey() {
        return DATA_SOURCE_KEY.get();
    }

    /**
     * 清除数据源key
     */
    public static void clear() {
        DATA_SOURCE_KEY.remove();
    }

    /**
     * 切换到主库（写库）
     */
    public static void useMaster() {
        setDataSourceKey(MASTER);
    }

    /**
     * 切换到从库（读库）
     */
    public static void useSlave() {
        setDataSourceKey(SLAVE);
    }

    /**
     * 切换到默认数据源（主库）
     */
    public static void useDefault() {
        clear();
    }
}
