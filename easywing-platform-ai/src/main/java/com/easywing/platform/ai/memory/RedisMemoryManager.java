package com.easywing.platform.ai.memory;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.model.ConversationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RedisMemoryManager implements MemoryManager {

    private static final String KEY_PREFIX = "ai:memory:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public RedisMemoryManager(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<ConversationContext> getContext(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        ConversationContext context = objectMapper.readValue(json, ConversationContext.class);
                        return Mono.just(context);
                    } catch (Exception e) {
                        log.error("Failed to deserialize context: {}", conversationId, e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    ConversationContext context = ConversationContext.builder()
                            .conversationId(conversationId)
                            .messages(new ArrayList<>())
                            .createdAt(Instant.now().toEpochMilli())
                            .updatedAt(Instant.now().toEpochMilli())
                            .build();
                    return Mono.just(context);
                }));
    }

    @Override
    public Mono<ConversationContext> saveContext(ConversationContext context) {
        String key = KEY_PREFIX + context.getConversationId();
        Duration ttl = Duration.ofHours(properties.getMemory().getTtlHours());

        try {
            String json = objectMapper.writeValueAsString(context);
            return redisTemplate.opsForValue().set(key, json, ttl).thenReturn(context);
        } catch (Exception e) {
            log.error("Failed to serialize context: {}", context.getConversationId(), e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> deleteContext(String conversationId) {
        return redisTemplate.delete(KEY_PREFIX + conversationId).then();
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
}