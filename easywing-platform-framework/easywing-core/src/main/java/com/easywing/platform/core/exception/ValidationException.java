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
package com.easywing.platform.core.exception;

import java.io.Serial;
import java.util.List;

/**
 * 验证异常
 * <p>
 * 用于表示请求参数验证失败，支持多字段错误信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class ValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 验证错误列表
     */
    private final List<ValidationError> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(new ValidationError(null, message));
    }

    public ValidationException(String field, String message) {
        super(message);
        this.errors = List.of(new ValidationError(field, message));
    }

    public ValidationException(List<ValidationError> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    /**
     * 验证错误详情
     */
    public record ValidationError(String field, String message) {
    }
}
