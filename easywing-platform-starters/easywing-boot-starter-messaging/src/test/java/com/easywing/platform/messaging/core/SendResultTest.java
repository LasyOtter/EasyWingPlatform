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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SendResult class
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class SendResultTest {

    @Test
    void testSuccessWithoutMetadata() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";

        // When
        SendResult result = SendResult.success(destination, messageId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(destination, result.getDestination());
        assertEquals(messageId, result.getMessageId());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
        assertNull(result.getError());
    }

    @Test
    void testSuccessWithMetadata() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("partition", 5);
        metadata.put("offset", 1000L);
        metadata.put("timestamp", System.currentTimeMillis());

        // When
        SendResult result = SendResult.success(destination, messageId, metadata);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(destination, result.getDestination());
        assertEquals(messageId, result.getMessageId());
        assertNotNull(result.getMetadata());
        assertEquals(3, result.getMetadata().size());
        assertEquals(5, result.getMetadata("partition"));
        assertEquals(1000L, result.getMetadata("offset"));
        assertNotNull(result.getMetadata("timestamp"));
        assertNull(result.getError());
    }

    @Test
    void testFailure() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";
        Throwable error = new RuntimeException("Send failed");

        // When
        SendResult result = SendResult.failure(destination, messageId, error);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(destination, result.getDestination());
        assertEquals(messageId, result.getMessageId());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
        assertNotNull(result.getError());
        assertEquals("Send failed", result.getError().getMessage());
    }

    @Test
    void testGetMetadataByKey() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", 42);

        // When
        SendResult result = SendResult.success(destination, messageId, metadata);

        // Then
        assertEquals("value1", result.getMetadata("key1"));
        assertEquals(42, result.getMetadata("key2"));
        assertNull(result.getMetadata("nonexistent"));
    }

    @Test
    void testMetadataImmutability() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");

        // When
        SendResult result = SendResult.success(destination, messageId, metadata);
        Map<String, Object> retrievedMetadata = result.getMetadata();
        retrievedMetadata.put("key2", "value2");

        // Then - original metadata should not be affected
        assertEquals(1, result.getMetadata().size());
        assertNull(result.getMetadata("key2"));
    }

    @Test
    void testSuccessWithNullMetadata() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";

        // When
        SendResult result = SendResult.success(destination, messageId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void testToString() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-123";
        SendResult successResult = SendResult.success(destination, messageId);
        SendResult failureResult = SendResult.failure(destination, messageId, 
            new RuntimeException("Error"));

        // When
        String successString = successResult.toString();
        String failureString = failureResult.toString();

        // Then
        assertNotNull(successString);
        assertTrue(successString.contains("success=true"));
        assertTrue(successString.contains("destination='test-topic'"));
        assertTrue(successString.contains("messageId='msg-123'"));
        
        assertNotNull(failureString);
        assertTrue(failureString.contains("success=false"));
        assertTrue(failureString.contains("error="));
    }

    @Test
    void testSuccessResultWithComplexMetadata() {
        // Given
        String destination = "test-queue";
        String messageId = "msg-456";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exchange", "test-exchange");
        metadata.put("routingKey", "test.routing.key");
        metadata.put("deliveryMode", 2);
        metadata.put("priority", 5);
        metadata.put("expiration", "60000");

        // When
        SendResult result = SendResult.success(destination, messageId, metadata);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("test-exchange", result.getMetadata("exchange"));
        assertEquals("test.routing.key", result.getMetadata("routingKey"));
        assertEquals(2, result.getMetadata("deliveryMode"));
        assertEquals(5, result.getMetadata("priority"));
        assertEquals("60000", result.getMetadata("expiration"));
    }

    @Test
    void testFailureWithDifferentExceptionTypes() {
        // Given
        String destination = "test-topic";
        String messageId = "msg-789";

        // When
        SendResult result1 = SendResult.failure(destination, messageId, 
            new IllegalArgumentException("Invalid argument"));
        SendResult result2 = SendResult.failure(destination, messageId, 
            new NullPointerException("Null pointer"));
        SendResult result3 = SendResult.failure(destination, messageId, 
            new Exception("Generic exception"));

        // Then
        assertFalse(result1.isSuccess());
        assertTrue(result1.getError() instanceof IllegalArgumentException);
        
        assertFalse(result2.isSuccess());
        assertTrue(result2.getError() instanceof NullPointerException);
        
        assertFalse(result3.isSuccess());
        assertTrue(result3.getError() instanceof Exception);
    }

    @Test
    void testMultipleSuccessResultsAreIndependent() {
        // Given
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("key", "value1");
        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("key", "value2");

        // When
        SendResult result1 = SendResult.success("dest1", "msg1", metadata1);
        SendResult result2 = SendResult.success("dest2", "msg2", metadata2);

        // Then
        assertEquals("value1", result1.getMetadata("key"));
        assertEquals("value2", result2.getMetadata("key"));
        assertEquals("dest1", result1.getDestination());
        assertEquals("dest2", result2.getDestination());
    }
}
