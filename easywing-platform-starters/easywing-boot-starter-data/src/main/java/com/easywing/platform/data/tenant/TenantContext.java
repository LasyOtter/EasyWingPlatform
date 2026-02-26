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
package com.easywing.platform.data.tenant;

/**
 * 多租户上下文
 * <p>
 * 用于在当前线程中存储和获取租户ID
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 设置当前线程的租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前线程的租户ID
     *
     * @return 租户ID，如果未设置则返回null
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 检查是否设置了租户ID
     *
     * @return 是否设置了租户ID
     */
    public static boolean hasTenantId() {
        return TENANT_ID.get() != null;
    }

    /**
     * 清除当前线程的租户ID
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
