package com.easywing.platform.ai.memory;

import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆管理器内存实现
 * <p>
 * 当 {@code easywing.ai.memory.type} 不是 {@code redis} 时生效，供本地开发使用。
 * 容量上限取 {@code easywing.ai.memory.maxMessages}，超出后丢弃最旧消息。
 * 重启即丢失，集群下不共享，禁止用于生产。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.memory", name = "type",
        havingValue = "memory", matchIfMissing = true)
public class InMemoryMemoryManager implements MemoryManager {

    private final ConcurrentHashMap<String, ConversationContext> contexts = new ConcurrentHashMap<>();
    private final int maxMessages;

    public InMemoryMemoryManager(com.easywing.platform.ai.config.AiProperties properties) {
        this.maxMessages = properties.getMemory().getMaxMessages();
        log.warn("AI 记忆使用内存存储（easywing.ai.memory.type=memory，maxMessages={}）："
                + "重启即丢失，集群下不共享，禁止用于生产", maxMessages);
    }

    @Override
    public Mono<ConversationContext> getContext(String conversationId) {
        ConversationContext context = contexts.computeIfAbsent(conversationId, id -> {
            long now = Instant.now().toEpochMilli();
            return ConversationContext.builder()
                    .conversationId(id)
                    .messages(new ArrayList<>())
                    .toolResults(new ArrayList<>())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        });
        return Mono.just(context);
    }

    @Override
    public Mono<ConversationContext> saveContext(ConversationContext context) {
        trim(context);
        context.setUpdatedAt(Instant.now().toEpochMilli());
        contexts.put(context.getConversationId(), context);
        return Mono.just(context);
    }

    @Override
    public Mono<Void> deleteContext(String conversationId) {
        contexts.remove(conversationId);
        return Mono.empty();
    }

    @Override
    public Mono<ConversationContext> addMessage(String conversationId, ChatMessage message) {
        return getContext(conversationId)
                .flatMap(context -> {
                    context.getMessages().add(message);
                    context.setMessageCount(context.getMessages().size());
                    context.setUpdatedAt(Instant.now().toEpochMilli());
                    return saveContext(context);
                });
    }

    /**
     * 按 {@code maxMessages} 截断最早的消息，避免内存无限增长。
     */
    private void trim(ConversationContext context) {
        var messages = context.getMessages();
        if (messages != null && messages.size() > maxMessages && maxMessages > 0) {
            messages.subList(0, messages.size() - maxMessages).clear();
            context.setMessageCount(messages.size());
        }
    }
}
