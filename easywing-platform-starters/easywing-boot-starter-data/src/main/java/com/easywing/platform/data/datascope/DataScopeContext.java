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
package com.easywing.platform.data.datascope;

/**
 * 数据权限上下文
 * <p>
 * 用于在ThreadLocal中存储数据权限相关的上下文信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class DataScopeContext {

    private DataScopeContext() {}

    private static final ThreadLocal<Boolean> IGNORE_DATA_SCOPE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 设置是否忽略数据权限
     *
     * @param ignore true表示忽略数据权限
     */
    public static void setIgnoreDataScope(boolean ignore) {
        IGNORE_DATA_SCOPE.set(ignore);
    }

    /**
     * 判断是否忽略数据权限
     *
     * @return true表示忽略数据权限
     */
    public static boolean isIgnoreDataScope() {
        return Boolean.TRUE.equals(IGNORE_DATA_SCOPE.get());
    }

    /**
     * 清除数据权限上下文
     */
    public static void clear() {
        IGNORE_DATA_SCOPE.remove();
    }
}
