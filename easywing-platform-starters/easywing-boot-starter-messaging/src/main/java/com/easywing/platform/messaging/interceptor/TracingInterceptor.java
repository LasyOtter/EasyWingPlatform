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

import java.util.UUID;

public class TracingInterceptor implements MessageInterceptor {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";

    @Override
    public Message<?> preSend(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        
        String traceId = headers.get(TRACE_ID, String.class);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            headers.set(TRACE_ID, traceId);
        }
        
        String spanId = UUID.randomUUID().toString();
        headers.set(SPAN_ID, spanId);
        
        return message;
    }

    @Override
    public void postSend(Message<?> message, SendResult result) {
    }

    @Override
    public Message<?> preReceive(Message<?> message) {
        return preSend(message);
    }

    @Override
    public void postReceive(Message<?> message, Object result) {
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
