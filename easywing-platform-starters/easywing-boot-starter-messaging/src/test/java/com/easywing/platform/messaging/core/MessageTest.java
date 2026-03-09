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
 * Unit tests for Message class
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class MessageTest {

    @Test
    void testCreateMessageWithPayload() {
        // Given
        String payload = "test payload";

        // When
        Message<String> message = new Message<>(payload);

        // Then
        assertNotNull(message);
        assertEquals(payload, message.getPayload());
        assertNotNull(message.getHeaders());
        assertNotNull(message.getHeaders().getId());
        assertNotNull(message.getHeaders().getTimestamp());
    }

    @Test
    void testCreateMessageWithPayloadAndHeaders() {
        // Given
        String payload = "test payload";
        MessageHeaders headers = new MessageHeaders();
        headers.set("customKey", "customValue");

        // When
        Message<String> message = new Message<>(payload, headers);

        // Then
        assertNotNull(message);
        assertEquals(payload, message.getPayload());
        assertEquals(headers, message.getHeaders());
        assertEquals("customValue", message.getHeaders().get("customKey"));
    }

    @Test
    void testCreateMessageWithNullPayloadThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Message<>(null)
        );
        assertEquals("Payload cannot be null", exception.getMessage());
    }

    @Test
    void testCreateMessageWithNullHeadersThrowsException() {
        // Given
        String payload = "test payload";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Message<>(payload, null)
        );
        assertEquals("Headers cannot be null", exception.getMessage());
    }

    @Test
    void testBuilderCreatesMessageWithPayload() {
        // Given
        String payload = "test payload";

        // When
        Message<String> message = Message.builder(payload).build();

        // Then
        assertNotNull(message);
        assertEquals(payload, message.getPayload());
        assertNotNull(message.getHeaders());
        assertNotNull(message.getHeaders().getId());
    }

    @Test
    void testBuilderSetHeader() {
        // Given
        String payload = "test payload";

        // When
        Message<String> message = Message.builder(payload)
            .setHeader("customKey", "customValue")
            .build();

        // Then
        assertEquals("customValue", message.getHeaders().get("customKey"));
    }

    @Test
    void testBuilderSetKey() {
        // Given
        String payload = "test payload";
        String key = "partition-key";

        // When
        Message<String> message = Message.builder(payload)
            .setKey(key)
            .build();

        // Then
        assertEquals(key, message.getHeaders().get(MessageHeaders.MESSAGE_KEY));
    }

    @Test
    void testBuilderSetPartition() {
        // Given
        String payload = "test payload";
        Integer partition = 5;

        // When
        Message<String> message = Message.builder(payload)
            .setPartition(partition)
            .build();

        // Then
        assertEquals(partition, message.getHeaders().get(MessageHeaders.PARTITION));
    }

    @Test
    void testBuilderSetContentType() {
        // Given
        String payload = "test payload";
        String contentType = "application/json";

        // When
        Message<String> message = Message.builder(payload)
            .setContentType(contentType)
            .build();

        // Then
        assertEquals(contentType, message.getHeaders().get(MessageHeaders.CONTENT_TYPE));
    }

    @Test
    void testBuilderChaining() {
        // Given
        String payload = "test payload";

        // When
        Message<String> message = Message.builder(payload)
            .setKey("key1")
            .setPartition(3)
            .setContentType("application/json")
            .setHeader("custom1", "value1")
            .setHeader("custom2", "value2")
            .build();

        // Then
        assertEquals(payload, message.getPayload());
        assertEquals("key1", message.getHeaders().get(MessageHeaders.MESSAGE_KEY));
        assertEquals(3, message.getHeaders().get(MessageHeaders.PARTITION));
        assertEquals("application/json", message.getHeaders().get(MessageHeaders.CONTENT_TYPE));
        assertEquals("value1", message.getHeaders().get("custom1"));
        assertEquals("value2", message.getHeaders().get("custom2"));
    }

    @Test
    void testMessageWithComplexPayload() {
        // Given
        TestPayload payload = new TestPayload("test", 123);

        // When
        Message<TestPayload> message = Message.builder(payload)
            .setKey("test-key")
            .build();

        // Then
        assertNotNull(message);
        assertEquals(payload, message.getPayload());
        assertEquals("test", message.getPayload().getName());
        assertEquals(123, message.getPayload().getValue());
    }

    @Test
    void testToString() {
        // Given
        String payload = "test payload";
        Message<String> message = Message.builder(payload)
            .setKey("test-key")
            .build();

        // When
        String result = message.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("payload="));
        assertTrue(result.contains("headers="));
    }

    @Test
    void testMessageImmutability() {
        // Given
        String payload = "test payload";
        Message<String> message = new Message<>(payload);
        
        // When
        String originalId = message.getHeaders().getId();
        message.getHeaders().set("newKey", "newValue");
        
        // Then - headers can be modified but payload and ID remain
        assertEquals(payload, message.getPayload());
        assertEquals(originalId, message.getHeaders().getId());
        assertEquals("newValue", message.getHeaders().get("newKey"));
    }

    /**
     * Test payload class
     */
    private static class TestPayload {
        private final String name;
        private final int value;

        public TestPayload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
