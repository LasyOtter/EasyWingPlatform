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
package com.easywing.platform.auth.store;

import com.easywing.platform.auth.domain.AuthUser;
import java.util.List;

/**
 * 用户存储抽象
 * <p>
 * 默认实现 {@code memory} 仅供单实例开发与测试；生产多实例部署使用 {@code redis}
 * 实现，使各实例共享同一份用户库（含邮箱验证状态与密码哈希）。
 * <p>
 * 约定：{@link #loadByUsername} / {@link #loadById} 在找不到用户时抛出
 * {@code INVALID_CREDENTIALS}；{@link #loadByEmail} 返回 {@code null} 以避免账号枚举。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface UserStore {

    /**
     * 按登录名加载用户，找不到时抛出业务异常。
     */
    AuthUser loadByUsername(String username);

    /**
     * 按用户ID加载用户，找不到时抛出业务异常。
     */
    AuthUser loadById(String userId);

    /**
     * 按邮箱加载用户；未找到返回 {@code null}。
     */
    AuthUser loadByEmail(String email);

    /**
     * 校验本地密码；外部身份用户（无本地密码哈希）恒为 {@code false}。
     */
    boolean validatePassword(AuthUser user, String rawPassword);

    /**
     * 注册自助用户。
     *
     * @throws IllegalArgumentException 邮箱已被占用
     */
    AuthUser register(String email, String rawPassword, boolean emailVerified, String tenantId);

    /**
     * 标记邮箱已验证；用户不存在返回 {@code null}。
     */
    AuthUser markEmailVerified(String userId);

    /**
     * 更新本地密码；用户不存在返回 {@code null}。
     */
    AuthUser updatePassword(String userId, String rawPassword);

    /**
     * 按外部身份幂等供应本地用户（LDAP/OAuth2）。
     */
    AuthUser findOrCreateExternal(String username, List<String> roles, String tenantId);
}
