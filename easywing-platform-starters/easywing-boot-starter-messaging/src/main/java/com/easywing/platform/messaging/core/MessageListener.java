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
 * 消息监听器接口
 * <p>
 * 用于处理接收到的消息。该接口是函数式接口，可以使用 Lambda 表达式实现。
 * <p>
 * 示例用法：
 * <pre>{@code
 * MessageListener<OrderEvent> listener = (message, context) -> {
 *     System.out.println("Received order: " + message);
 *     System.out.println("From destination: " + context.getDestination());
 * };
 * }</pre>
 *
 * @param <T> 消息类型
 * @author EasyWing Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface MessageListener<T> {

    /**
     * 处理接收到的消息
     * <p>
     * 当消息到达时，消息中间件适配器会调用此方法。实现类应该处理消息内容，
     * 并在处理成功时正常返回。如果处理失败，应该抛出异常，以便触发重试或
     * 死信队列逻辑。
     *
     * @param message 消息内容
     * @param context 消息上下文，包含消息的元数据和处理信息
     * @throws Exception 处理失败时抛出
     */
    void onMessage(T message, MessageContext context) throws Exception;
}
