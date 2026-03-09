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

/**
 * 消息
 * <p>
 * 包含消息负载和消息头的通用消息对象
 *
 * @param <T> 消息负载类型
 * @author EasyWing Team
 * @since 1.0.0
 */
public class Message<T> {

    private final T payload;
    private final MessageHeaders headers;

    /**
     * 构造消息
     *
     * @param payload 消息负载
     */
    public Message(T payload) {
        this(payload, new MessageHeaders());
    }

    /**
     * 构造消息
     *
     * @param payload 消息负载
     * @param headers 消息头
     */
    public Message(T payload, MessageHeaders headers) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (headers == null) {
            throw new IllegalArgumentException("Headers cannot be null");
        }
        this.payload = payload;
        this.headers = headers;
    }

    /**
     * 获取消息负载
     *
     * @return 消息负载
     */
    public T getPayload() {
        return payload;
    }

    /**
     * 获取消息头
     *
     * @return 消息头
     */
    public MessageHeaders getHeaders() {
        return headers;
    }

    /**
     * 创建消息构建器
     *
     * @param payload 消息负载
     * @param <T> 消息负载类型
     * @return 消息构建器
     */
    public static <T> MessageBuilder<T> builder(T payload) {
        return new MessageBuilder<>(payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "payload=" + payload +
                ", headers=" + headers +
                '}';
    }

    /**
     * 消息构建器
     *
     * @param <T> 消息负载类型
     */
    public static class MessageBuilder<T> {
        private final T payload;
        private final MessageHeaders headers;

        private MessageBuilder(T payload) {
            this.payload = payload;
            this.headers = new MessageHeaders();
        }

        /**
         * 设置消息头
         *
         * @param key 键
         * @param value 值
         * @return 构建器
         */
        public MessageBuilder<T> setHeader(String key, Object value) {
            headers.set(key, value);
            return this;
        }

        /**
         * 设置消息键（用于分区）
         *
         * @param key 消息键
         * @return 构建器
         */
        public MessageBuilder<T> setKey(String key) {
            headers.set(MessageHeaders.MESSAGE_KEY, key);
            return this;
        }

        /**
         * 设置分区
         *
         * @param partition 分区
         * @return 构建器
         */
        public MessageBuilder<T> setPartition(Integer partition) {
            headers.set(MessageHeaders.PARTITION, partition);
            return this;
        }

        /**
         * 设置内容类型
         *
         * @param contentType 内容类型
         * @return 构建器
         */
        public MessageBuilder<T> setContentType(String contentType) {
            headers.set(MessageHeaders.CONTENT_TYPE, contentType);
            return this;
        }

        /**
         * 构建消息
         *
         * @return 消息
         */
        public Message<T> build() {
            return new Message<>(payload, headers);
        }
    }
}
