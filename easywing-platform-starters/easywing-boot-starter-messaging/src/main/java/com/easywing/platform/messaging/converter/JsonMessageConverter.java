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
package com.easywing.platform.messaging.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JsonMessageConverter implements MessageConverter {

    private final ObjectMapper objectMapper;
    private final Set<Class<?>> supportedTypes = ConcurrentHashMap.newKeySet();

    public JsonMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        registerCommonTypes();
    }

    public JsonMessageConverter() {
        this(new ObjectMapper());
    }

    private void registerCommonTypes() {
        supportedTypes.add(String.class);
        supportedTypes.add(Integer.class);
        supportedTypes.add(Long.class);
        supportedTypes.add(Double.class);
        supportedTypes.add(Float.class);
        supportedTypes.add(Boolean.class);
        supportedTypes.add(byte[].class);
    }

    @Override
    public byte[] toBytes(Object object) throws ConversionException {
        if (object == null) {
            return null;
        }
        try {
            if (object instanceof byte[]) {
                return (byte[]) object;
            }
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new ConversionException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public <T> T fromBytes(byte[] bytes, Class<T> targetType) throws ConversionException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, targetType);
        } catch (IOException e) {
            throw new ConversionException("Failed to deserialize JSON to " + targetType.getName(), e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (supportedTypes.contains(type)) {
            return true;
        }
        return objectMapper.canSerialize(type);
    }
}
