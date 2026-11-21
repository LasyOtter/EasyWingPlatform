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
package com.easywing.platform.led.datasource;

/**
 * 数据源异常。生产环境的取数失败不应导致刷新线程整体退出，
 * 调用方（RefreshService）捕获后记录日志并跳过本轮。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class DataSourceException extends RuntimeException {

    private final String sourceType;
    private final String sourceId;

    public DataSourceException(String sourceType, String sourceId, String message) {
        super("[" + sourceType + (sourceId != null ? ":" + sourceId : "") + "] " + message);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }

    public DataSourceException(String sourceType, String sourceId, String message, Throwable cause) {
        super("[" + sourceType + (sourceId != null ? ":" + sourceId : "") + "] " + message, cause);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceId() {
        return sourceId;
    }
}
