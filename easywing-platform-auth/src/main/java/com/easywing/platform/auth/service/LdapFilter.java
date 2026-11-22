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
 * LDAP 搜索过滤器转义工具
 * <p>
 * 依据 RFC 4515 对断言值中的保留字符进行十六进制转义，避免搜索过滤器注入。
 * 仅转义过滤器断言值，不改变过滤器结构本身。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class LdapFilter {

    private LdapFilter() {}

    /**
     * 转义单个断言值。
     *
     * @param value 原始值，可为 {@code null}
     * @return 转义后的值，{@code null} 返回空字符串
     */
    public static String encode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\5c");
                case '*' -> escaped.append("\\2a");
                case '(' -> escaped.append("\\28");
                case ')' -> escaped.append("\\29");
                case 0 -> escaped.append("\\00");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * 使用模板构建过滤器，将 {@code {0}} 占位符替换为转义后的用户输入。
     *
     * @param template 过滤器模板，例如 {@code (uid={0})}
     * @param value 用户输入
     * @return 安全的过滤器表达式
     */
    public static String build(String template, String value) {
        return template.replace("{0}", encode(value));
    }
}
