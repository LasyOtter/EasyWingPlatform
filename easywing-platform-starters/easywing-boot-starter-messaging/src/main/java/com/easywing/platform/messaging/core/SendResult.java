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

/**
 * 发送结果
 * <p>
 * 包含消息发送的状态和元数据
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class SendResult {

    private final boolean success;
    private final String destination;
    private final String messageId;
    private final Map<String, Object> metadata;
    private final Throwable error;

    /**
     * 构造发送结果
     *
     * @param success 是否成功
     * @param destination 目标
     * @param messageId 消息ID
     * @param metadata 元数据
     * @param error 错误信息
     */
    private SendResult(boolean success, String destination, String messageId, 
                      Map<String, Object> metadata, Throwable error) {
        this.success = success;
        this.destination = destination;
        this.messageId = messageId;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.error = error;
    }

    /**
     * 创建成功结果
     *
     * @param destination 目标
     * @param messageId 消息ID
     * @return 发送结果
     */
    public static SendResult success(String destination, String messageId) {
        return new SendResult(true, destination, messageId, null, null);
    }

    /**
     * 创建成功结果（带元数据）
     *
     * @param destination 目标
     * @param messageId 消息ID
     * @param metadata 元数据
     * @return 发送结果
     */
    public static SendResult success(String destination, String messageId, Map<String, Object> metadata) {
        return new SendResult(true, destination, messageId, metadata, null);
    }

    /**
     * 创建失败结果
     *
     * @param destination 目标
     * @param messageId 消息ID
     * @param error 错误信息
     * @return 发送结果
     */
    public static SendResult failure(String destination, String messageId, Throwable error) {
        return new SendResult(false, destination, messageId, null, error);
    }

    /**
     * 是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
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
     * 获取消息ID
     *
     * @return 消息ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 获取元数据
     *
     * @return 元数据
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * 获取元数据值
     *
     * @param key 键
     * @return 值
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "SendResult{" +
                "success=" + success +
                ", destination='" + destination + '\'' +
                ", messageId='" + messageId + '\'' +
                ", metadata=" + metadata +
                ", error=" + error +
                '}';
    }
}
