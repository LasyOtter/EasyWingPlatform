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
package com.easywing.platform.messaging.adapter;

import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageListener;
import com.easywing.platform.messaging.core.MessagingException;
import com.easywing.platform.messaging.core.SendCallback;
import com.easywing.platform.messaging.core.SendResult;

/**
 * 消息中间件适配器接口
 * <p>
 * 定义与具体消息中间件交互的标准方法。该接口为不同的消息中间件（如 Kafka、RabbitMQ）
 * 提供统一的抽象层，使得上层业务代码可以无缝切换底层消息中间件实现。
 * <p>
 * 实现类需要封装特定消息中间件的客户端 API，并将其转换为统一的接口调用。
 * <p>
 * 示例实现：
 * <pre>{@code
 * public class KafkaMessagingAdapter implements MessagingAdapter {
 *     private final KafkaTemplate<String, byte[]> kafkaTemplate;
 *     
 *     @Override
 *     public SendResult doSend(String destination, Message<?> message) {
 *         // 使用 KafkaTemplate 发送消息
 *         return ...;
 *     }
 *     
 *     @Override
 *     public String getAdapterType() {
 *         return "kafka";
 *     }
 * }
 * }</pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface MessagingAdapter {

    /**
     * 同步发送消息
     * <p>
     * 将消息发送到指定的目标（主题或队列），并等待发送完成。该方法会阻塞当前线程，
     * 直到消息被成功发送或发送失败。
     * <p>
     * 实现类应该：
     * <ul>
     *   <li>将消息负载序列化为字节数组</li>
     *   <li>提取消息头中的分区键、分区号等元数据</li>
     *   <li>调用底层消息中间件的发送 API</li>
     *   <li>将底层的发送结果转换为 {@link SendResult}</li>
     *   <li>在发送失败时抛出 {@link MessagingException}</li>
     * </ul>
     *
     * @param destination 目标（主题/队列名称）
     * @param message 消息对象，包含负载和消息头
     * @return 发送结果，包含发送状态和元数据
     * @throws MessagingException 发送失败时抛出
     */
    SendResult doSend(String destination, Message<?> message) throws MessagingException;

    /**
     * 异步发送消息
     * <p>
     * 将消息异步发送到指定的目标，并通过回调接口通知发送结果。该方法会立即返回，
     * 不会阻塞当前线程。当消息发送完成（成功或失败）时，会调用回调接口的相应方法。
     * <p>
     * 实现类应该：
     * <ul>
     *   <li>使用底层消息中间件的异步发送 API</li>
     *   <li>在发送成功时调用 {@link SendCallback#onSuccess(SendResult)}</li>
     *   <li>在发送失败时调用 {@link SendCallback#onFailure(MessagingException)}</li>
     *   <li>确保回调方法在适当的线程中执行</li>
     * </ul>
     *
     * @param destination 目标（主题/队列名称）
     * @param message 消息对象，包含负载和消息头
     * @param callback 发送回调接口，用于接收发送结果
     */
    void doSendAsync(String destination, Message<?> message, SendCallback callback);

    /**
     * 注册消息监听器
     * <p>
     * 为指定的目标（主题或队列）注册一个消息监听器。当有消息到达该目标时，
     * 适配器会调用监听器的 {@link MessageListener#onMessage(Object, com.easywing.platform.messaging.core.MessageContext)}
     * 方法来处理消息。
     * <p>
     * 实现类应该：
     * <ul>
     *   <li>创建底层消息中间件的消费者或监听器容器</li>
     *   <li>配置消费者的相关参数（如消费者组、确认模式等）</li>
     *   <li>在接收到消息时，将其反序列化并调用监听器</li>
     *   <li>根据监听器的执行结果确认或拒绝消息</li>
     *   <li>处理监听器抛出的异常，触发重试或死信队列逻辑</li>
     * </ul>
     *
     * @param destination 目标（主题/队列名称）
     * @param listener 消息监听器
     */
    void registerListener(String destination, MessageListener<?> listener);

    /**
     * 获取适配器类型
     * <p>
     * 返回该适配器对应的消息中间件类型标识。该标识用于配置选择、日志记录和监控。
     * <p>
     * 标准的适配器类型包括：
     * <ul>
     *   <li>"kafka" - Apache Kafka</li>
     *   <li>"rabbitmq" - RabbitMQ</li>
     *   <li>"rocketmq" - Apache RocketMQ</li>
     *   <li>"activemq" - Apache ActiveMQ</li>
     * </ul>
     *
     * @return 适配器类型标识（小写字符串）
     */
    String getAdapterType();
}
