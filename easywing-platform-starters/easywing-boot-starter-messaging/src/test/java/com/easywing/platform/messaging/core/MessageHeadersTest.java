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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageHeaders class
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class MessageHeadersTest {

    @Test
    void testDefaultConstructorGeneratesUniqueId() {
        // When
        MessageHeaders headers1 = new MessageHeaders();
        MessageHeaders headers2 = new MessageHeaders();

        // Then
        assertNotNull(headers1.getId());
        assertNotNull(headers2.getId());
        assertNotEquals(headers1.getId(), headers2.getId());
    }

    @Test
    void testDefaultConstructorSetsTimestamp() {
        // Given
        long beforeCreation = System.currentTimeMillis();

        // When
        MessageHeaders headers = new MessageHeaders();

        // Then
        long afterCreation = System.currentTimeMillis();
        assertNotNull(headers.getTimestamp());
        assertTrue(headers.getTimestamp() >= beforeCreation);
        assertTrue(headers.getTimestamp() <= afterCreation);
    }

    @Test
    void testConstructorWithMapPreservesHeaders() {
        // Given
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put("customKey", "customValue");
        initialHeaders.put("numericKey", 42);

        // When
        MessageHeaders headers = new MessageHeaders(initialHeaders);

        // Then
        assertEquals("customValue", headers.get("customKey"));
        assertEquals(42, headers.get("numericKey"));
    }

    @Test
    void testConstructorWithMapGeneratesIdIfMissing() {
        // Given
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put("customKey", "customValue");

        // When
        MessageHeaders headers = new MessageHeaders(initialHeaders);

        // Then
        assertNotNull(headers.getId());
        assertEquals("customValue", headers.get("customKey"));
    }

    @Test
    void testConstructorWithMapPreservesExistingId() {
        // Given
        String existingId = "existing-id-123";
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put(MessageHeaders.MESSAGE_ID, existingId);

        // When
        MessageHeaders headers = new MessageHeaders(initialHeaders);

        // Then
        assertEquals(existingId, headers.getId());
    }

    @Test
    void testConstructorWithMapGeneratesTimestampIfMissing() {
        // Given
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put("customKey", "customValue");

        // When
        MessageHeaders headers = new MessageHeaders(initialHeaders);

        // Then
        assertNotNull(headers.getTimestamp());
    }

    @Test
    void testConstructorWithMapPreservesExistingTimestamp() {
        // Given
        Long existingTimestamp = 1234567890L;
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put(MessageHeaders.TIMESTAMP, existingTimestamp);

        // When
        MessageHeaders headers = new MessageHeaders(initialHeaders);

        // Then
        assertEquals(existingTimestamp, headers.getTimestamp());
    }

    @Test
    void testSetAndGetHeader() {
        // Given
        MessageHeaders headers = new MessageHeaders();

        // When
        headers.set("testKey", "testValue");

        // Then
        assertEquals("testValue", headers.get("testKey"));
    }

    @Test
    void testGetTypeSafeWithCorrectType() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("stringKey", "stringValue");
        headers.set("intKey", 123);
        headers.set("longKey", 456L);

        // When & Then
        assertEquals("stringValue", headers.get("stringKey", String.class));
        assertEquals(123, headers.get("intKey", Integer.class));
        assertEquals(456L, headers.get("longKey", Long.class));
    }

    @Test
    void testGetTypeSafeWithIncorrectTypeThrowsException() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("stringKey", "stringValue");

        // When & Then
        ClassCastException exception = assertThrows(
            ClassCastException.class,
            () -> headers.get("stringKey", Integer.class)
        );
        assertTrue(exception.getMessage().contains("stringKey"));
        assertTrue(exception.getMessage().contains("Integer"));
    }

    @Test
    void testGetTypeSafeReturnsNullForMissingKey() {
        // Given
        MessageHeaders headers = new MessageHeaders();

        // When
        String result = headers.get("nonExistentKey", String.class);

        // Then
        assertNull(result);
    }

    @Test
    void testContainsKey() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("existingKey", "value");

        // When & Then
        assertTrue(headers.containsKey("existingKey"));
        assertTrue(headers.containsKey(MessageHeaders.MESSAGE_ID));
        assertTrue(headers.containsKey(MessageHeaders.TIMESTAMP));
        assertFalse(headers.containsKey("nonExistentKey"));
    }

    @Test
    void testToMapReturnsAllHeaders() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("key1", "value1");
        headers.set("key2", "value2");

        // When
        Map<String, Object> map = headers.toMap();

        // Then
        assertTrue(map.containsKey(MessageHeaders.MESSAGE_ID));
        assertTrue(map.containsKey(MessageHeaders.TIMESTAMP));
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    void testToMapReturnsNewInstance() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("originalKey", "originalValue");

        // When
        Map<String, Object> map = headers.toMap();
        map.put("newKey", "newValue");

        // Then
        assertFalse(headers.containsKey("newKey"));
        assertEquals("originalValue", headers.get("originalKey"));
    }

    @Test
    void testForEachIteratesAllHeaders() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("key1", "value1");
        headers.set("key2", "value2");

        // When
        AtomicInteger count = new AtomicInteger(0);
        headers.forEach((key, value) -> count.incrementAndGet());

        // Then
        assertTrue(count.get() >= 4); // At least MESSAGE_ID, TIMESTAMP, key1, key2
    }

    @Test
    void testCommonHeaderConstants() {
        // Then
        assertEquals("messageId", MessageHeaders.MESSAGE_ID);
        assertEquals("timestamp", MessageHeaders.TIMESTAMP);
        assertEquals("messageKey", MessageHeaders.MESSAGE_KEY);
        assertEquals("partition", MessageHeaders.PARTITION);
        assertEquals("contentType", MessageHeaders.CONTENT_TYPE);
    }

    @Test
    void testSetCommonHeaders() {
        // Given
        MessageHeaders headers = new MessageHeaders();

        // When
        headers.set(MessageHeaders.MESSAGE_KEY, "partition-key-123");
        headers.set(MessageHeaders.PARTITION, 5);
        headers.set(MessageHeaders.CONTENT_TYPE, "application/json");

        // Then
        assertEquals("partition-key-123", headers.get(MessageHeaders.MESSAGE_KEY));
        assertEquals(5, headers.get(MessageHeaders.PARTITION));
        assertEquals("application/json", headers.get(MessageHeaders.CONTENT_TYPE));
    }

    @Test
    void testToStringContainsHeaders() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("testKey", "testValue");

        // When
        String result = headers.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("MessageHeaders"));
        assertTrue(result.contains("testKey"));
    }

    @Test
    void testOverwriteExistingHeader() {
        // Given
        MessageHeaders headers = new MessageHeaders();
        headers.set("key", "originalValue");

        // When
        headers.set("key", "newValue");

        // Then
        assertEquals("newValue", headers.get("key"));
    }

    @Test
    void testSetNullValue() {
        // Given
        MessageHeaders headers = new MessageHeaders();

        // When
        headers.set("nullKey", null);

        // Then
        assertTrue(headers.containsKey("nullKey"));
        assertNull(headers.get("nullKey"));
    }
}
