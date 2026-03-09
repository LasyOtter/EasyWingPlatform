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

import java.util.concurrent.CompletableFuture;

/**
 * 消息发送模板接口
 * <p>
 * 提供统一的消息发送能力，支持同步、异步和事务消息发送。
 * 该接口是消息发送的核心抽象，屏蔽了底层消息中间件的差异。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 同步发送
 * SendResult result = messageTemplate.send("my-topic", myMessage);
 * 
 * // 异步发送
 * CompletableFuture<SendResult> future = messageTemplate.sendAsync("my-topic", myMessage);
 * 
 * // 事务发送
 * SendResult result = messageTemplate.sendInTransaction("my-topic", myMessage, () -> {
 *     // 执行业务逻辑
 *     return true;
 * });
 * }</pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface MessageTemplate {
    
    /**
     * 同步发送消息到指定目标
     * <p>
     * 该方法会阻塞当前线程直到消息发送完成。消息发送成功后返回包含发送状态和元数据的结果对象。
     * 
     * @param <T> 消息类型
     * @param destination 目标（主题/队列），格式取决于底层消息中间件
     * @param message 消息内容，将被自动序列化
     * @return 发送结果，包含发送状态、消息ID和元数据
     * @throws MessagingException 当消息发送失败时抛出
     */
    <T> SendResult send(String destination, T message) throws MessagingException;
    
    /**
     * 同步发送消息到指定目标（带分区键）
     * <p>
     * 该方法允许指定分区键，用于确定消息发送到哪个分区。
     * 对于 Kafka，分区键用于计算消息的分区；对于 RabbitMQ，可用于路由。
     * 
     * @param <T> 消息类型
     * @param destination 目标（主题/队列）
     * @param key 分区键，用于确定消息的分区或路由
     * @param message 消息内容
     * @return 发送结果
     * @throws MessagingException 当消息发送失败时抛出
     */
    <T> SendResult send(String destination, String key, T message) throws MessagingException;
    
    /**
     * 异步发送消息到指定目标
     * <p>
     * 该方法立即返回一个 CompletableFuture，不会阻塞当前线程。
     * 可以通过返回的 Future 对象获取发送结果或注册回调。
     * 
     * @param <T> 消息类型
     * @param destination 目标（主题/队列）
     * @param message 消息内容
     * @return CompletableFuture，异步完成时包含发送结果
     */
    <T> CompletableFuture<SendResult> sendAsync(String destination, T message);
    
    /**
     * 异步发送消息到指定目标（带回调）
     * <p>
     * 该方法立即返回，不会阻塞当前线程。
     * 当消息发送完成时，会调用提供的回调对象的相应方法。
     * 
     * @param <T> 消息类型
     * @param destination 目标（主题/队列）
     * @param message 消息内容
     * @param callback 发送回调，用于处理发送成功或失败的情况
     */
    <T> void sendAsync(String destination, T message, SendCallback callback);
    
    /**
     * 在事务中发送消息
     * <p>
     * 该方法确保消息发送与业务操作的原子性。首先执行事务执行器中的业务逻辑，
     * 只有当业务逻辑执行成功时才会发送消息。如果业务逻辑执行失败，消息不会被发送；
     * 如果业务逻辑成功但消息发送失败，会抛出异常。
     * <p>
     * 注意：事务的具体实现取决于底层消息中间件的支持。
     * 
     * @param <T> 消息类型
     * @param destination 目标（主题/队列）
     * @param message 消息内容
     * @param transactionExecutor 事务执行器，包含需要在事务中执行的业务逻辑
     * @return 发送结果
     * @throws MessagingException 当事务执行失败或消息发送失败时抛出
     */
    <T> SendResult sendInTransaction(String destination, T message, 
                                     TransactionExecutor transactionExecutor) throws MessagingException;
}
