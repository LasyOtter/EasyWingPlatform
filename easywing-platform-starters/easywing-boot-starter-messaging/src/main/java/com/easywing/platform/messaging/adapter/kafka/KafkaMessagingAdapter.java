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
package com.easywing.platform.messaging.adapter.kafka;

import com.easywing.platform.messaging.adapter.MessagingAdapter;
import com.easywing.platform.messaging.converter.MessageConverter;
import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageHeaders;
import com.easywing.platform.messaging.core.MessageListener;
import com.easywing.platform.messaging.core.MessagingException;
import com.easywing.platform.messaging.core.SendCallback;
import com.easywing.platform.messaging.core.SendResult;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class KafkaMessagingAdapter implements MessagingAdapter {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final MessageConverter converter;
    private final Map<String, MessageListener<?>> listeners;

    public KafkaMessagingAdapter(KafkaTemplate<String, byte[]> kafkaTemplate,
                                 MessageConverter converter) {
        this.kafkaTemplate = kafkaTemplate;
        this.converter = converter;
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public SendResult doSend(String destination, Message<?> message) throws MessagingException {
        try {
            byte[] payload = converter.toBytes(message.getPayload());
            ProducerRecord<String, byte[]> record = createProducerRecord(destination, message, payload);

            org.springframework.kafka.support.SendResult<String, byte[]> result = kafkaTemplate.send(record).get(30, TimeUnit.SECONDS);

            return convertToSendResult(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Kafka send interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new MessagingException("Kafka send failed", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new MessagingException("Kafka send timeout", e);
        } catch (Exception e) {
            throw new MessagingException("Kafka send failed", e);
        }
    }

    @Override
    public void doSendAsync(String destination, Message<?> message, SendCallback callback) {
        try {
            byte[] payload = converter.toBytes(message.getPayload());
            ProducerRecord<String, byte[]> record = createProducerRecord(destination, message, payload);

            CompletableFuture<org.springframework.kafka.support.SendResult<String, byte[]>> future = kafkaTemplate.send(record);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    callback.onFailure(new MessagingException("Kafka async send failed", ex));
                } else if (result != null) {
                    callback.onSuccess(convertToSendResult(result));
                } else {
                    callback.onFailure(new MessagingException("Kafka async send returned null result"));
                }
            });
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
        return "kafka";
    }

    private ProducerRecord<String, byte[]> createProducerRecord(String topic,
                                                                 Message<?> message,
                                                                 byte[] payload) {
        String key = message.getHeaders().get(MessageHeaders.MESSAGE_KEY, String.class);
        Integer partition = message.getHeaders().get(MessageHeaders.PARTITION, Integer.class);

        ProducerRecord<String, byte[]> record;
        if (partition != null && partition >= 0) {
            record = new ProducerRecord<>(topic, partition, key, payload);
        } else {
            record = new ProducerRecord<>(topic, key, payload);
        }

        message.getHeaders().forEach((k, v) -> {
            if (v != null) {
                record.headers().add(new RecordHeader(k, String.valueOf(v).getBytes(StandardCharsets.UTF_8)));
            }
        });

        return record;
    }

    private SendResult convertToSendResult(org.springframework.kafka.support.SendResult<String, byte[]> result) {
        if (result == null) {
            return SendResult.failure(null, null, new MessagingException("Kafka result is null"));
        }

        String topic = result.getRecordMetadata().topic();
        int partition = result.getRecordMetadata().partition();
        long offset = result.getRecordMetadata().offset();
        long timestamp = result.getRecordMetadata().timestamp();

        return SendResult.success(topic, String.valueOf(offset), Map.of(
            "partition", partition,
            "offset", offset,
            "timestamp", timestamp
        ));
    }
}
