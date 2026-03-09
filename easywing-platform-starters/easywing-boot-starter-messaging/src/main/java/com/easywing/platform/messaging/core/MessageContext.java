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
 * 消息上下文
 * <p>
 * 包含消息的元数据和处理信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class MessageContext {

    private final String destination;
    private final MessageHeaders headers;
    private final String adapterType;
    private final long receivedTimestamp;

    /**
     * 构造消息上下文
     *
     * @param destination 目标
     * @param headers 消息头
     * @param adapterType 适配器类型
     */
    public MessageContext(String destination, MessageHeaders headers, String adapterType) {
        this.destination = destination;
        this.headers = headers;
        this.adapterType = adapterType;
        this.receivedTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取目标
     *
     * @return 目标
     */
    public String getDestination() {
        return destination;
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
     * 获取适配器类型
     *
     * @return 适配器类型
     */
    public String getAdapterType() {
        return adapterType;
    }

    /**
     * 获取接收时间戳
     *
     * @return 接收时间戳
     */
    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }

    /**
     * 获取消息ID
     *
     * @return 消息ID
     */
    public String getMessageId() {
        return headers.getId();
    }

    @Override
    public String toString() {
        return "MessageContext{" +
                "destination='" + destination + '\'' +
                ", headers=" + headers +
                ", adapterType='" + adapterType + '\'' +
                ", receivedTimestamp=" + receivedTimestamp +
                '}';
    }
}
