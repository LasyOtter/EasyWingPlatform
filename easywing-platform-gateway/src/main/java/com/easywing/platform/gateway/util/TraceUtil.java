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
package com.easywing.platform.gateway.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 链路追踪工具类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class TraceUtil {

    private static final int TRACE_ID_LENGTH = 32;
    private static final int SPAN_ID_LENGTH = 16;

    private TraceUtil() {
    }

    public static String generateTraceId() {
        return generateId(TRACE_ID_LENGTH);
    }

    public static String generateSpanId() {
        return generateId(SPAN_ID_LENGTH);
    }

    public static String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateId(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }

    public static boolean isValidTraceId(String traceId) {
        if (traceId == null || traceId.length() != TRACE_ID_LENGTH) {
            return false;
        }
        for (char c : traceId.toCharArray()) {
            if (!isHexChar(c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidSpanId(String spanId) {
        if (spanId == null || spanId.length() != SPAN_ID_LENGTH) {
            return false;
        }
        for (char c : spanId.toCharArray()) {
            if (!isHexChar(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}