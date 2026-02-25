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
package com.easywing.platform.core.util;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 业务断言工具类
 * <p>
 * 用于业务逻辑中的前置条件检查，失败时抛出BizException
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class BizAssert {

    private BizAssert() {
    }

    // ==================== 通用断言 ====================

    public static void isTrue(boolean expression, String errorCode, String message) {
        if (!expression) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isTrue(boolean expression, String errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    public static void isFalse(boolean expression, String errorCode, String message) {
        if (expression) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 对象断言 ====================

    public static void notNull(Object object, String errorCode) {
        if (object == null) {
            throw new BizException(errorCode);
        }
    }

    public static void notNull(Object object, String errorCode, String message) {
        if (object == null) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isNull(Object object, String errorCode, String message) {
        if (object != null) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 字符串断言 ====================

    public static void notEmpty(String text, String errorCode, String message) {
        if (text == null || text.isEmpty()) {
            throw new BizException(errorCode, message);
        }
    }

    public static void notBlank(String text, String errorCode, String message) {
        if (text == null || text.isBlank()) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 集合断言 ====================

    public static void notEmpty(Collection<?> collection, String errorCode, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(errorCode, message);
        }
    }

    public static void notEmpty(Map<?, ?> map, String errorCode, String message) {
        if (map == null || map.isEmpty()) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isEmpty(Collection<?> collection, String errorCode, String message) {
        if (collection != null && !collection.isEmpty()) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 数值断言 ====================

    public static void isPositive(Number number, String errorCode, String message) {
        if (number == null || number.doubleValue() <= 0) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isNotNegative(Number number, String errorCode, String message) {
        if (number == null || number.doubleValue() < 0) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 状态断言 ====================

    public static void state(boolean expression, String errorCode, String message) {
        if (!expression) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 资源断言 ====================

    public static <T> T notFound(T object, String message) {
        if (object == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, message);
        }
        return object;
    }

    public static void exists(Object object, String errorCode, String message) {
        if (object != null) {
            throw new BizException(errorCode, message);
        }
    }

    public static void notExists(Object object, String errorCode, String message) {
        if (object != null) {
            throw new BizException(errorCode, message);
        }
    }

    // ==================== 相等断言 ====================

    public static void equals(Object obj1, Object obj2, String errorCode, String message) {
        if (!Objects.equals(obj1, obj2)) {
            throw new BizException(errorCode, message);
        }
    }

    public static void notEquals(Object obj1, Object obj2, String errorCode, String message) {
        if (Objects.equals(obj1, obj2)) {
            throw new BizException(errorCode, message);
        }
    }
}
