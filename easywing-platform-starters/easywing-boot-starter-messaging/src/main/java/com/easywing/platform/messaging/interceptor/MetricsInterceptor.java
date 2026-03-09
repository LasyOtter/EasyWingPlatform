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
package com.easywing.platform.messaging.interceptor;

import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageHeaders;
import com.easywing.platform.messaging.core.SendResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsInterceptor implements MessageInterceptor {

    private final Map<String, AtomicLong> sendSuccessCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sendFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sendLatencies = new ConcurrentHashMap<>();
    
    private final Map<String, AtomicLong> receiveSuccessCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> receiveFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> receiveLatencies = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message) {
        message.getHeaders().set("sendStartTime", System.currentTimeMillis());
        return message;
    }

    @Override
    public void postSend(Message<?> message, SendResult result) {
        String destination = message.getHeaders().get(MessageHeaders.PARTITION) != null 
            ? message.getHeaders().get(MessageHeaders.PARTITION).toString() 
            : "unknown";
        
        Long startTime = message.getHeaders().get("sendStartTime", Long.class);
        if (startTime != null) {
            long latency = System.currentTimeMillis() - startTime;
            sendLatencies.computeIfAbsent(destination, k -> new AtomicLong()).addAndGet(latency);
        }

        if (result != null && result.isSuccess()) {
            sendSuccessCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        } else {
            sendFailureCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    public Message<?> preReceive(Message<?> message) {
        message.getHeaders().set("receiveStartTime", System.currentTimeMillis());
        return message;
    }

    @Override
    public void postReceive(Message<?> message, Object result) {
        String destination = "unknown";
        
        Long startTime = message.getHeaders().get("receiveStartTime", Long.class);
        if (startTime != null) {
            long latency = System.currentTimeMillis() - startTime;
            receiveLatencies.computeIfAbsent(destination, k -> new AtomicLong()).addAndGet(latency);
        }

        if (result == null) {
            receiveSuccessCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        } else {
            receiveFailureCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    public int getOrder() {
        return 100;
    }

    public long getSendSuccessCount(String destination) {
        AtomicLong counter = sendSuccessCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    public long getSendFailureCount(String destination) {
        AtomicLong counter = sendFailureCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    public long getReceiveSuccessCount(String destination) {
        AtomicLong counter = receiveSuccessCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    public long getReceiveFailureCount(String destination) {
        AtomicLong counter = receiveFailureCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }
}
