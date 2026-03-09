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
package com.easywing.platform.messaging.interceptor;

import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.SendResult;

/**
 * 消息拦截器接口
 * <p>
 * 用于在消息发送和接收前后执行自定义逻辑，例如追踪、监控和消息增强等功能。
 * 拦截器按照 {@link #getOrder()} 方法返回的顺序值执行，数值越小越先执行。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface MessageInterceptor {

    /**
     * 发送前拦截
     * <p>
     * 在消息发送前调用，可以对消息进行增强或修改。
     *
     * @param message 原始消息
     * @return 处理后的消息
     */
    Message<?> preSend(Message<?> message);

    /**
     * 发送后拦截
     * <p>
     * 在消息发送后调用，可以记录发送结果或执行清理操作。
     *
     * @param message 消息
     * @param result 发送结果
     */
    void postSend(Message<?> message, SendResult result);

    /**
     * 接收前拦截
     * <p>
     * 在消息接收前调用，可以对消息进行预处理或验证。
     *
     * @param message 接收到的消息
     * @return 处理后的消息
     */
    Message<?> preReceive(Message<?> message);

    /**
     * 接收后拦截
     * <p>
     * 在消息接收后调用，可以记录处理结果或执行清理操作。
     *
     * @param message 消息
     * @param result 处理结果
     */
    void postReceive(Message<?> message, Object result);

    /**
     * 拦截器顺序
     * <p>
     * 返回拦截器的执行顺序，数值越小越先执行。
     * 默认返回 0。
     *
     * @return 顺序值，越小越先执行
     */
    default int getOrder() {
        return 0;
    }
}
