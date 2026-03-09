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
package com.easywing.platform.messaging.adapter.rabbitmq;

import com.easywing.platform.messaging.adapter.MessagingAdapter;
import com.easywing.platform.messaging.converter.MessageConverter;
import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageListener;
import com.easywing.platform.messaging.core.MessagingException;
import com.easywing.platform.messaging.core.SendCallback;
import com.easywing.platform.messaging.core.SendResult;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMQMessagingAdapter implements MessagingAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter converter;
    private final Map<String, MessageListener<?>> listeners;

    public RabbitMQMessagingAdapter(RabbitTemplate rabbitTemplate,
                                     MessageConverter converter) {
        this.rabbitTemplate = rabbitTemplate;
        this.converter = converter;
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public SendResult doSend(String destination, Message<?> message) throws MessagingException {
        try {
            byte[] payload = converter.toBytes(message.getPayload());

            String[] parts = parseDestination(destination);
            String exchange = parts[0];
            String routingKey = parts[1];

            org.springframework.amqp.core.Message amqpMessage = createAmqpMessage(message, payload);

            rabbitTemplate.send(exchange, routingKey, amqpMessage);

            return SendResult.success(destination, message.getHeaders().getId());
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("RabbitMQ send failed", e);
        }
    }

    @Override
    public void doSendAsync(String destination, Message<?> message, SendCallback callback) {
        try {
            byte[] payload = converter.toBytes(message.getPayload());

            String[] parts = parseDestination(destination);
            String exchange = parts[0];
            String routingKey = parts[1];

            org.springframework.amqp.core.Message amqpMessage = createAmqpMessage(message, payload);

            CorrelationData correlationData = new CorrelationData(message.getHeaders().getId());
            correlationData.getFuture().whenComplete((result, ex) -> {
                if (ex != null) {
                    callback.onFailure(new MessagingException("RabbitMQ async send failed", ex));
                } else if (result != null && result.isAck()) {
                    callback.onSuccess(SendResult.success(destination, message.getHeaders().getId()));
                } else {
                    callback.onFailure(new MessagingException("Message not acknowledged"));
                }
            });

            rabbitTemplate.send(exchange, routingKey, amqpMessage, correlationData);
        } catch (Exception e) {
            callback.onFailure(new MessagingException("Failed to prepare message", e));
        }
    }

    @Override
    public void registerListener(String destination, MessageListener<?> listener) {
        listeners.put(destination, listener);
    }

    @Override
    public String getAdapterType() {
        return "rabbitmq";
    }

    private String[] parseDestination(String destination) {
        if (destination.contains("/")) {
            String[] parts = destination.split("/", 2);
            return parts;
        }
        return new String[]{"", destination};
    }

    private org.springframework.amqp.core.Message createAmqpMessage(Message<?> message, byte[] payload) {
        org.springframework.amqp.core.MessageProperties properties = new org.springframework.amqp.core.MessageProperties();
        
        message.getHeaders().forEach((k, v) -> {
            if (v != null) {
                properties.setHeader(k, v);
            }
        });

        return new org.springframework.amqp.core.Message(payload, properties);
    }
}
