package com.easywing.platform.ai.session;

import com.easywing.platform.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class RedisSessionManager implements SessionManager {

    private static final String SESSION_KEY_PREFIX = "ai:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "ai:user:sessions:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public RedisSessionManager(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<ConversationSession> createSession(String userId, String sessionName) {
        String sessionId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        ConversationSession session = ConversationSession.builder()
                .id(sessionId)
                .userId(userId)
                .name(sessionName != null ? sessionName : "New Session")
                .createdAt(now)
                .updatedAt(now)
                .lastActiveAt(now)
                .messageCount(0)
                .archived(false)
                .build();

        return saveSession(session);
    }

    @Override
    public Mono<Optional<ConversationSession>> getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(Optional.of(objectMapper.readValue(json, ConversationSession.class)));
                    } catch (Exception e) {
                        log.error("Failed to deserialize session: {}", sessionId, e);
                        return Mono.just(Optional.<ConversationSession>empty());
                    }
                })
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<List<ConversationSession>> getUserSessions(String userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        return redisTemplate.opsForSet().members(userSessionsKey)
                .flatMap(sessionId -> {
                    String key = SESSION_KEY_PREFIX + sessionId;
                    return redisTemplate.opsForValue().get(key);
                })
                .flatMap(json -> {
                    try {
                        return Flux.just(objectMapper.readValue(json, ConversationSession.class));
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .collectList();
    }

    @Override
    public Mono<ConversationSession> updateSession(ConversationSession session) {
        session.setUpdatedAt(Instant.now().toEpochMilli());
        return saveSession(session);
    }

    @Override
    public Mono<Void> deleteSession(String sessionId) {
        return getSession(sessionId)
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        ConversationSession session = optional.get();
                        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + session.getUserId();
                        return redisTemplate.opsForSet().remove(userSessionsKey, sessionId)
                                .then(redisTemplate.delete(SESSION_KEY_PREFIX + sessionId));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<ConversationSession> saveSession(ConversationSession session) {
        String key = SESSION_KEY_PREFIX + session.getId();
        Duration ttl = Duration.ofSeconds(properties.getSession().getDefaultTtlSeconds());

        try {
            String json = objectMapper.writeValueAsString(session);
            return redisTemplate.opsForValue().set(key, json, ttl).thenReturn(session);
        } catch (Exception e) {
            log.error("Failed to serialize session: {}", session.getId(), e);
            return Mono.error(e);
        }
    }
}