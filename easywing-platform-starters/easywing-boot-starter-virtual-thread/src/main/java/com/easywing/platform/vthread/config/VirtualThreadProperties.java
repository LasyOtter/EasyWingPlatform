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
package com.easywing.platform.vthread.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 虚拟线程配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.threads.virtual")
public class VirtualThreadProperties {

    /**
     * 是否启用虚拟线程
     */
    private boolean enabled = true;

    /**
     * 是否在日志中输出虚拟线程名称
     */
    private boolean logThreadName = false;

    /**
     * 是否检测阻塞操作
     */
    private boolean detectPinnedThreads = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogThreadName() {
        return logThreadName;
    }

    public void setLogThreadName(boolean logThreadName) {
        this.logThreadName = logThreadName;
    }

    public boolean isDetectPinnedThreads() {
        return detectPinnedThreads;
    }

    public void setDetectPinnedThreads(boolean detectPinnedThreads) {
        this.detectPinnedThreads = detectPinnedThreads;
    }
}
