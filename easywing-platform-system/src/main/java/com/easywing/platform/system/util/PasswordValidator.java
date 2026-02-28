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
package com.easywing.platform.system.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 密码强度校验工具类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class PasswordValidator {

    /**
     * 密码强度正则：至少1个大写字母、1个小写字母、1个数字、1个特殊字符，长度至少8位
     */
    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

    /**
     * 校验密码强度
     *
     * @param password 待校验的密码
     * @return 是否为强密码
     */
    public boolean isStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        return STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 校验密码强度（带最小长度参数）
     *
     * @param password        待校验的密码
     * @param minPasswordLength 最小密码长度
     * @return 是否为强密码
     */
    public boolean isStrong(String password, int minPasswordLength) {
        if (password == null || password.length() < minPasswordLength) {
            return false;
        }
        return STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 获取密码强度要求描述
     *
     * @return 密码强度要求描述
     */
    public String getStrengthDescription() {
        return "密码必须至少8位，包含大小写字母、数字和特殊字符(@$!%*?&)";
    }

    /**
     * 获取密码强度要求描述（带最小长度参数）
     *
     * @param minPasswordLength 最小密码长度
     * @return 密码强度要求描述
     */
    public String getStrengthDescription(int minPasswordLength) {
        return String.format("密码必须至少%d位，包含大小写字母、数字和特殊字符(@$!%*?&)", minPasswordLength);
    }
}
