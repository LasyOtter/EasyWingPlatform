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
package com.easywing.platform.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户模块配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "easywing.user")
public class UserProperties {

    /**
     * 默认密码（生产环境必须通过环境变量或配置中心设置）
     */
    private String defaultPassword = "ChangeMe@123";

    /**
     * 最小密码长度
     */
    private Integer minPasswordLength = 8;

    /**
     * 最大登录失败次数
     */
    private Integer maxFailedAttempts = 5;

    /**
     * 账户锁定时间（分钟）
     */
    private Integer lockDurationMinutes = 30;

    /**
     * 密码历史检查数量
     */
    private Integer passwordHistoryCount = 5;

    /**
     * 密码过期天数
     */
    private Integer passwordExpireDays = 90;

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }

    public Integer getMinPasswordLength() {
        return minPasswordLength;
    }

    public void setMinPasswordLength(Integer minPasswordLength) {
        this.minPasswordLength = minPasswordLength;
    }

    public Integer getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(Integer maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Integer getLockDurationMinutes() {
        return lockDurationMinutes;
    }

    public void setLockDurationMinutes(Integer lockDurationMinutes) {
        this.lockDurationMinutes = lockDurationMinutes;
    }

    public Integer getPasswordHistoryCount() {
        return passwordHistoryCount;
    }

    public void setPasswordHistoryCount(Integer passwordHistoryCount) {
        this.passwordHistoryCount = passwordHistoryCount;
    }

    public Integer getPasswordExpireDays() {
        return passwordExpireDays;
    }

    public void setPasswordExpireDays(Integer passwordExpireDays) {
        this.passwordExpireDays = passwordExpireDays;
    }
}
