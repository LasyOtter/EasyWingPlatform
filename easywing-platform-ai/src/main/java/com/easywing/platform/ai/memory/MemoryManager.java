package com.easywing.platform.ai.memory;

import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MemoryManager {
    Mono<ConversationContext> getContext(String conversationId);
    Mono<ConversationContext> saveContext(ConversationContext context);
    Mono<Void> deleteContext(String conversationId);
    Mono<ConversationContext> addMessage(String conversationId, ChatMessage message);
}