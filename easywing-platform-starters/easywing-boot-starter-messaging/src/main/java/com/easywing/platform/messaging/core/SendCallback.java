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
 * 消息发送回调接口
 * <p>
 * 用于异步消息发送的回调处理，当消息发送完成时会调用相应的方法。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface SendCallback {
    
    /**
     * 发送成功时调用
     *
     * @param result 发送结果
     */
    void onSuccess(SendResult result);
    
    /**
     * 发送失败时调用
     *
     * @param exception 异常信息
     */
    void onFailure(MessagingException exception);
}
