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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 网关 JSON 序列化工具
 * <p>
 * 通过 {@code GatewayAutoConfiguration} 用 Spring 容器中的
 * {@link ObjectMapper} 初始化，保证错误响应的序列化行为与业务响应一致。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class JsonCodec {

    private static volatile ObjectMapper objectMapper;

    private JsonCodec() {}

    /**
     * 使用容器中的 ObjectMapper 初始化
     */
    public static void init(ObjectMapper mapper) {
        objectMapper = mapper;
    }

    public static byte[] encode(Object value) {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            // 尚未初始化时退化为默认 ObjectMapper，避免启动阶段直接失败
            mapper = new ObjectMapper();
        }
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode response body", e);
        }
    }
}
