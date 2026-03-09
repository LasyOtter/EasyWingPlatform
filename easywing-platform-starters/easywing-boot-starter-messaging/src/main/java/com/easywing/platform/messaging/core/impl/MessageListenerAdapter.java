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
package com.easywing.platform.messaging.core.impl;

import com.easywing.platform.messaging.adapter.MessagingAdapter;
import com.easywing.platform.messaging.converter.MessageConverter;
import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageContext;
import com.easywing.platform.messaging.core.MessageHeaders;
import com.easywing.platform.messaging.core.MessageListener;
import com.easywing.platform.messaging.core.MessagingException;
import com.easywing.platform.messaging.interceptor.MessageInterceptor;

import java.util.Comparator;
import java.util.List;

public class MessageListenerAdapter {

    private final MessagingAdapter adapter;
    private final MessageConverter converter;
    private final List<MessageInterceptor> interceptors;
    private final String adapterType;

    public MessageListenerAdapter(MessagingAdapter adapter,
                                   MessageConverter converter,
                                   List<MessageInterceptor> interceptors) {
        this.adapter = adapter;
        this.converter = converter;
        this.interceptors = sortInterceptors(interceptors);
        this.adapterType = adapter.getAdapterType();
    }

    public void handleMessage(byte[] rawMessage, String destination, MessageHeaders headers) {
        Message<?> message = new Message<>(rawMessage, headers);
        
        message = applyPreReceiveInterceptors(message);

        MessageContext context = new MessageContext(destination, headers, adapterType);

        try {
            Object payload = converter.fromBytes(rawMessage, Object.class);
            Message<Object> convertedMessage = new Message<>(payload, headers);
            
            @SuppressWarnings("unchecked")
            MessageListener<Object> listener = (MessageListener<Object>) getListener(destination);
            if (listener != null) {
                listener.onMessage(payload, context);
            }
            
            applyPostReceiveInterceptors(message, null);
        } catch (Exception e) {
            applyPostReceiveInterceptors(message, e);
            throw new MessagingException("Message processing failed", e);
        }
    }

    private MessageListener<?> getListener(String destination) {
        return null;
    }

    private List<MessageInterceptor> sortInterceptors(List<MessageInterceptor> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            return List.of();
        }
        return interceptors.stream()
            .sorted(Comparator.comparingInt(MessageInterceptor::getOrder))
            .toList();
    }

    private Message<?> applyPreReceiveInterceptors(Message<?> message) {
        Message<?> current = message;
        for (MessageInterceptor interceptor : interceptors) {
            current = interceptor.preReceive(current);
        }
        return current;
    }

    private void applyPostReceiveInterceptors(Message<?> message, Object result) {
        for (MessageInterceptor interceptor : interceptors) {
            interceptor.postReceive(message, result);
        }
    }
}
