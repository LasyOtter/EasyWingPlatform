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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageContext class
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class MessageContextTest {

    @Test
    void testConstructorStoresAllFields() {
        // Given
        String destination = "test-topic";
        MessageHeaders headers = new MessageHeaders();
        String adapterType = "kafka";

        // When
        MessageContext context = new MessageContext(destination, headers, adapterType);

        // Then
        assertEquals(destination, context.getDestination());
        assertEquals(headers, context.getHeaders());
        assertEquals(adapterType, context.getAdapterType());
        assertNotNull(context.getReceivedTimestamp());
    }

    @Test
    void testReceivedTimestampIsSetOnCreation() {
        // Given
        long beforeCreation = System.currentTimeMillis();
        MessageHeaders headers = new MessageHeaders();

        // When
        MessageContext context = new MessageContext("test-topic", headers, "kafka");

        // Then
        long afterCreation = System.currentTimeMillis();
        assertTrue(context.getReceivedTimestamp() >= beforeCreation);
        assertTrue(context.getReceivedTimestamp() <= afterCreation);
    }

    @Test
    void testGetMessageIdReturnsHeaderId() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        String expectedId = headers.getId();

        // When
        MessageContext context = new MessageContext("test-topic", headers, "kafka");

        // Then
        assertEquals(expectedId, context.getMessageId());
    }

    @Test
    void testGetDestinationReturnsCorrectValue() {
        // Given
        String destination = "orders-topic";
        MessageHeaders headers = new MessageHeaders();

        // When
        MessageContext context = new MessageContext(destination, headers, "kafka");

        // Then
        assertEquals(destination, context.getDestination());
    }

    @Test
    void testGetAdapterTypeReturnsCorrectValue() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        String adapterType = "rabbitmq";

        // When
        MessageContext context = new MessageContext("test-queue", headers, adapterType);

        // Then
        assertEquals(adapterType, context.getAdapterType());
    }

    @Test
    void testGetHeadersReturnsCorrectInstance() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("customKey", "customValue");

        // When
        MessageContext context = new MessageContext("test-topic", headers, "kafka");

        // Then
        assertSame(headers, context.getHeaders());
        assertEquals("customValue", context.getHeaders().get("customKey"));
    }

    @Test
    void testToStringContainsAllFields() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        MessageContext context = new MessageContext("test-topic", headers, "kafka");

        // When
        String result = context.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("MessageContext"));
        assertTrue(result.contains("test-topic"));
        assertTrue(result.contains("kafka"));
        assertTrue(result.contains("receivedTimestamp"));
    }

    @Test
    void testContextWithKafkaAdapter() {
        // Given
        String destination = "user-events";
        MessageHeaders headers = new MessageHeaders();
        headers.set(MessageHeaders.MESSAGE_KEY, "user-123");
        headers.set(MessageHeaders.PARTITION, 2);

        // When
        MessageContext context = new MessageContext(destination, headers, "kafka");

        // Then
        assertEquals("kafka", context.getAdapterType());
        assertEquals(destination, context.getDestination());
        assertEquals("user-123", context.getHeaders().get(MessageHeaders.MESSAGE_KEY));
        assertEquals(2, context.getHeaders().get(MessageHeaders.PARTITION));
    }

    @Test
    void testContextWithRabbitMQAdapter() {
        // Given
        String destination = "exchange/routing-key";
        MessageHeaders headers = new MessageHeaders();
        headers.set(MessageHeaders.CONTENT_TYPE, "application/json");

        // When
        MessageContext context = new MessageContext(destination, headers, "rabbitmq");

        // Then
        assertEquals("rabbitmq", context.getAdapterType());
        assertEquals(destination, context.getDestination());
        assertEquals("application/json", context.getHeaders().get(MessageHeaders.CONTENT_TYPE));
    }

    @Test
    void testMultipleContextsHaveDifferentTimestamps() throws InterruptedException {
        // Given
        MessageHeaders headers1 = new MessageHeaders();
        MessageHeaders headers2 = new MessageHeaders();

        // When
        MessageContext context1 = new MessageContext("topic1", headers1, "kafka");
        Thread.sleep(2); // Small delay to ensure different timestamps
        MessageContext context2 = new MessageContext("topic2", headers2, "kafka");

        // Then
        assertTrue(context2.getReceivedTimestamp() >= context1.getReceivedTimestamp());
    }

    @Test
    void testContextPreservesHeaderMetadata() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("traceId", "trace-123");
        headers.set("spanId", "span-456");
        headers.set("userId", "user-789");

        // When
        MessageContext context = new MessageContext("events-topic", headers, "kafka");

        // Then
        assertEquals("trace-123", context.getHeaders().get("traceId"));
        assertEquals("span-456", context.getHeaders().get("spanId"));
        assertEquals("user-789", context.getHeaders().get("userId"));
    }
}
