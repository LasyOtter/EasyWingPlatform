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
package com.easywing.platform.auth.service;

/**
 * LDAP 目录条目
 *
 * @param dn 条目可辨识名称（用于绑定认证）
 * @param email 邮箱属性，可能为 {@code null}
 * @param displayName 显示名属性，可能为 {@code null}
 * @author EasyWing Team
 * @since 1.0.0
 */
public record LdapEntry(String dn, String email, String displayName) {}
