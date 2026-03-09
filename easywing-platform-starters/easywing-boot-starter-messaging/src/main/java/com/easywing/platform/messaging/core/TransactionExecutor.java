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
 * 事务执行器接口
 * <p>
 * 用于在事务消息发送中执行业务逻辑。只有当业务逻辑执行成功时，消息才会被发送。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface TransactionExecutor {
    
    /**
     * 执行事务逻辑
     * <p>
     * 该方法包含需要在事务中执行的业务逻辑。
     * 返回 true 表示执行成功，消息将被发送；
     * 返回 false 或抛出异常表示执行失败，消息不会被发送。
     *
     * @return 执行结果，true 表示成功，false 表示失败
     * @throws Exception 执行过程中的异常
     */
    boolean execute() throws Exception;
}
