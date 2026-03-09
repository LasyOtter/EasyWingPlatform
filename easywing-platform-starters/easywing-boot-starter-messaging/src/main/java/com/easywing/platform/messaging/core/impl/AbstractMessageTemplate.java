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
import com.easywing.platform.messaging.config.MessagingProperties;
import com.easywing.platform.messaging.converter.MessageConverter;
import com.easywing.platform.messaging.core.Message;
import com.easywing.platform.messaging.core.MessageHeaders;
import com.easywing.platform.messaging.core.MessagingException;
import com.easywing.platform.messaging.core.SendCallback;
import com.easywing.platform.messaging.core.SendResult;
import com.easywing.platform.messaging.core.TransactionExecutor;
import com.easywing.platform.messaging.interceptor.MessageInterceptor;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractMessageTemplate implements com.easywing.platform.messaging.core.MessageTemplate {

    protected final MessagingAdapter adapter;
    protected final MessageConverter converter;
    protected final List<MessageInterceptor> interceptors;
    protected final MessagingProperties properties;

    public AbstractMessageTemplate(MessagingAdapter adapter,
                                   MessageConverter converter,
                                   List<MessageInterceptor> interceptors,
                                   MessagingProperties properties) {
        this.adapter = adapter;
        this.converter = converter;
        this.interceptors = sortInterceptors(interceptors);
        this.properties = properties;
    }

    @Override
    public <T> SendResult send(String destination, T message) throws MessagingException {
        Message<?> wrappedMessage = wrapMessage(message);
        wrappedMessage = applyPreSendInterceptors(wrappedMessage);

        try {
            SendResult result = adapter.doSend(destination, wrappedMessage);
            applyPostSendInterceptors(wrappedMessage, result);
            return result;
        } catch (MessagingException e) {
            applyPostSendInterceptors(wrappedMessage, SendResult.failure(destination, 
                wrappedMessage.getHeaders().getId(), e));
            throw e;
        } catch (Exception e) {
            MessagingException messagingException = new MessagingException("Failed to send message", e);
            applyPostSendInterceptors(wrappedMessage, SendResult.failure(destination, 
                wrappedMessage.getHeaders().getId(), messagingException));
            throw messagingException;
        }
    }

    @Override
    public <T> SendResult send(String destination, String key, T message) throws MessagingException {
        Message<T> wrappedMessage = wrapMessageWithKey(message, key);
        Message<?> intercepted = applyPreSendInterceptors(wrappedMessage);

        try {
            SendResult result = adapter.doSend(destination, intercepted);
            applyPostSendInterceptors(intercepted, result);
            return result;
        } catch (MessagingException e) {
            applyPostSendInterceptors(intercepted, SendResult.failure(destination, 
                intercepted.getHeaders().getId(), e));
            throw e;
        } catch (Exception e) {
            MessagingException messagingException = new MessagingException("Failed to send message", e);
            applyPostSendInterceptors(wrappedMessage, SendResult.failure(destination, 
                wrappedMessage.getHeaders().getId(), messagingException));
            throw messagingException;
        }
    }

    @Override
    public <T> CompletableFuture<SendResult> sendAsync(String destination, T message) {
        Message<?> wrappedMessage = wrapMessage(message);
        final Message<?> intercepted = applyPreSendInterceptors(wrappedMessage);

        CompletableFuture<SendResult> future = new CompletableFuture<>();
        
        adapter.doSendAsync(destination, intercepted, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                applyPostSendInterceptors(intercepted, result);
                future.complete(result);
            }

            @Override
            public void onFailure(MessagingException exception) {
                applyPostSendInterceptors(intercepted, SendResult.failure(destination, 
                    intercepted.getHeaders().getId(), exception));
                future.completeExceptionally(exception);
            }
        });

        return future;
    }

    @Override
    public <T> void sendAsync(String destination, T message, SendCallback callback) {
        Message<?> wrappedMessage = wrapMessage(message);
        final Message<?> intercepted = applyPreSendInterceptors(wrappedMessage);

        adapter.doSendAsync(destination, intercepted, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                applyPostSendInterceptors(intercepted, result);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(MessagingException exception) {
                applyPostSendInterceptors(intercepted, SendResult.failure(destination, 
                    intercepted.getHeaders().getId(), exception));
                callback.onFailure(exception);
            }
        });
    }

    @Override
    public <T> SendResult sendInTransaction(String destination, T message, 
                                            TransactionExecutor transactionExecutor) throws MessagingException {
        Message<?> wrappedMessage = wrapMessage(message);
        
        boolean transactionResult;
        try {
            transactionResult = transactionExecutor.execute();
        } catch (Exception e) {
            throw new MessagingException("Transaction execution failed", e);
        }

        if (!transactionResult) {
            throw new MessagingException("Transaction returned false, message will not be sent");
        }

        wrappedMessage = applyPreSendInterceptors(wrappedMessage);

        try {
            SendResult result = adapter.doSend(destination, wrappedMessage);
            applyPostSendInterceptors(wrappedMessage, result);
            return result;
        } catch (MessagingException e) {
            applyPostSendInterceptors(wrappedMessage, SendResult.failure(destination, 
                wrappedMessage.getHeaders().getId(), e));
            throw new MessagingException("Transaction executed successfully but message sending failed", e);
        } catch (Exception e) {
            MessagingException messagingException = new MessagingException(
                "Transaction executed successfully but message sending failed", e);
            applyPostSendInterceptors(wrappedMessage, SendResult.failure(destination, 
                wrappedMessage.getHeaders().getId(), messagingException));
            throw messagingException;
        }
    }

    protected abstract Message<?> wrapMessage(Object payload);

    protected <T> Message<T> wrapMessageWithKey(Object payload, String key) {
        MessageHeaders headers = new MessageHeaders();
        if (key != null) {
            headers.set(MessageHeaders.MESSAGE_KEY, key);
        }
        return new Message<>((T) payload, headers);
    }

    protected List<MessageInterceptor> sortInterceptors(List<MessageInterceptor> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            return List.of();
        }
        return interceptors.stream()
            .sorted(Comparator.comparingInt(MessageInterceptor::getOrder))
            .toList();
    }

    protected Message<?> applyPreSendInterceptors(Message<?> message) {
        Message<?> current = message;
        for (MessageInterceptor interceptor : interceptors) {
            current = interceptor.preSend(current);
        }
        return current;
    }

    protected void applyPostSendInterceptors(Message<?> message, SendResult result) {
        for (MessageInterceptor interceptor : interceptors) {
            interceptor.postSend(message, result);
        }
    }
}
