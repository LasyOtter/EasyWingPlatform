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
package com.easywing.platform.data.interceptor;

/**
 * 数据权限SQL处理异常。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class DataScopeSqlException extends RuntimeException {

    public DataScopeSqlException(String message) {
        super(message);
    }

    public DataScopeSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
