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

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * 内置账号初始化
 * <p>
 * 密码只从环境变量读取；未配置时生成随机密码并输出到日志，避免硬编码弱口令。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
final class BuiltinUserSeeder {

    static final String ADMIN_ID = "1";
    static final String USER_ID = "2";

    private BuiltinUserSeeder() {}

    /**
     * 写入内置 admin / user 账号。
     *
     * @param passwordEncoder 密码编码器
     * @param register 接收内置账号的注册回调
     */
    static void seed(PasswordEncoder passwordEncoder, Register register) {
        register.apply(
                ADMIN_ID,
                "admin",
                passwordEncoder.encode(resolvePassword("ADMIN_PASSWORD")),
                List.of("ROLE_ADMIN", "ROLE_USER"));
        register.apply(USER_ID, "user", passwordEncoder.encode(resolvePassword("USER_PASSWORD")), List.of("ROLE_USER"));
    }

    /**
     * 解析内置账号密码：优先读取环境变量，未配置时生成随机密码并输出到日志。
     */
    static String resolvePassword(String envName) {
        String configured = System.getenv(envName);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String generated = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "A1!";
        log.warn(
                "Environment variable {} is not set. Generated a random password for the built-in"
                        + " account: {}. Set {} to a strong password before production.",
                envName,
                generated,
                envName);
        return generated;
    }

    @FunctionalInterface
    interface Register {
        void apply(String id, String username, String passwordHash, List<String> roles);
    }
}
