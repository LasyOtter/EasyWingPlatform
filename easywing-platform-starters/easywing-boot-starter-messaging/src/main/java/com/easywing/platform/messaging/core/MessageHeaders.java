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
package com.easywing.platform.messaging.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消息头
 * <p>
 * 存储消息的元数据信息，包括消息ID、时间戳、分区键等
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class MessageHeaders {

    /**
     * 消息ID头键
     */
    public static final String MESSAGE_ID = "messageId";

    /**
     * 时间戳头键
     */
    public static final String TIMESTAMP = "timestamp";

    /**
     * 消息键头键（用于分区）
     */
    public static final String MESSAGE_KEY = "messageKey";

    /**
     * 分区头键
     */
    public static final String PARTITION = "partition";

    /**
     * 内容类型头键
     */
    public static final String CONTENT_TYPE = "contentType";

    private final Map<String, Object> headers;

    /**
     * 构造消息头
     */
    public MessageHeaders() {
        this.headers = new HashMap<>();
        this.headers.put(MESSAGE_ID, UUID.randomUUID().toString());
        this.headers.put(TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 构造消息头
     *
     * @param headers 初始头信息
     */
    public MessageHeaders(Map<String, Object> headers) {
        this.headers = new HashMap<>(headers);
        if (!this.headers.containsKey(MESSAGE_ID)) {
            this.headers.put(MESSAGE_ID, UUID.randomUUID().toString());
        }
        if (!this.headers.containsKey(TIMESTAMP)) {
            this.headers.put(TIMESTAMP, System.currentTimeMillis());
        }
    }

    /**
     * 获取消息ID
     *
     * @return 消息ID
     */
    public String getId() {
        return (String) headers.get(MESSAGE_ID);
    }

    /**
     * 获取时间戳
     *
     * @return 时间戳
     */
    public Long getTimestamp() {
        return (Long) headers.get(TIMESTAMP);
    }

    /**
     * 设置头信息
     *
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        headers.put(key, value);
    }

    /**
     * 获取头信息
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return headers.get(key);
    }

    /**
     * 获取头信息（类型安全）
     *
     * @param key 键
     * @param type 类型
     * @param <T> 类型参数
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = headers.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new ClassCastException("Header value for key '" + key + "' is not of type " + type.getName());
    }

    /**
     * 判断是否包含指定键
     *
     * @param key 键
     * @return 是否包含
     */
    public boolean containsKey(String key) {
        return headers.containsKey(key);
    }

    /**
     * 获取所有头信息
     *
     * @return 头信息映射
     */
    public Map<String, Object> toMap() {
        return new HashMap<>(headers);
    }

    /**
     * 遍历所有头信息
     *
     * @param action 操作
     */
    public void forEach(java.util.function.BiConsumer<String, Object> action) {
        headers.forEach(action);
    }

    @Override
    public String toString() {
        return "MessageHeaders{" + headers + '}';
    }
}
