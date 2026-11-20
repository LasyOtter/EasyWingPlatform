package com.easywing.platform.ai.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器内存实现
 * <p>
 * 当 {@code easywing.ai.session.storage-type} 不是 {@code redis} 时生效，
 * 供本地开发与无 Redis 环境使用。数据仅存于进程内存，重启丢失，不适用于集群部署。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.ai.session", name = "storage-type",
        havingValue = "memory", matchIfMissing = true)
public class InMemorySessionManager implements SessionManager {

    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    public InMemorySessionManager() {
        log.warn("AI 会话使用内存存储（easywing.ai.session.storage-type=memory）："
                + "重启即丢失，集群下不共享，禁止用于生产");
    }

    @Override
    public Mono<ConversationSession> createSession(String userId, String sessionName) {
        long now = System.currentTimeMillis();
        ConversationSession session = ConversationSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(sessionName != null ? sessionName : "New Session")
                .createdAt(now)
                .updatedAt(now)
                .lastActiveAt(now)
                .messageCount(0)
                .archived(false)
                .build();
        sessions.put(session.getId(), session);
        return Mono.just(session);
    }

    @Override
    public Mono<Optional<ConversationSession>> getSession(String sessionId) {
        return Mono.just(Optional.ofNullable(sessions.get(sessionId)));
    }

    @Override
    public Mono<List<ConversationSession>> getUserSessions(String userId) {
        List<ConversationSession> result = sessions.values().stream()
                .filter(s -> userId != null && userId.equals(s.getUserId()))
                .sorted(Comparator.comparing(ConversationSession::getLastActiveAt).reversed())
                .toList();
        return Mono.just(result);
    }

    @Override
    public Mono<ConversationSession> updateSession(ConversationSession session) {
        session.setUpdatedAt(System.currentTimeMillis());
        sessions.put(session.getId(), session);
        return Mono.just(session);
    }

    @Override
    public Mono<Void> deleteSession(String sessionId) {
        sessions.remove(sessionId);
        return Mono.empty();
    }
}
