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
package com.easywing.platform.messaging.converter;

/**
 * 消息转换器接口
 * <p>
 * 负责消息的序列化和反序列化，将对象转换为字节数组以便在消息中间件中传输，
 * 以及将接收到的字节数组转换回对象。
 * <p>
 * 实现类应该处理转换过程中的异常，并抛出 {@link ConversionException}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface MessageConverter {

    /**
     * 将对象转换为字节数组
     * <p>
     * 此方法用于消息发送前的序列化操作。
     *
     * @param object 待转换对象
     * @return 字节数组
     * @throws ConversionException 转换失败时抛出
     */
    byte[] toBytes(Object object) throws ConversionException;

    /**
     * 将字节数组转换为对象
     * <p>
     * 此方法用于消息接收后的反序列化操作。
     *
     * @param bytes 字节数组
     * @param targetType 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的对象
     * @throws ConversionException 转换失败时抛出
     */
    <T> T fromBytes(byte[] bytes, Class<T> targetType) throws ConversionException;

    /**
     * 判断是否支持该类型
     * <p>
     * 用于确定转换器是否能够处理指定的类型。
     *
     * @param type 类型
     * @return 如果支持该类型返回 true，否则返回 false
     */
    boolean supports(Class<?> type);
}
