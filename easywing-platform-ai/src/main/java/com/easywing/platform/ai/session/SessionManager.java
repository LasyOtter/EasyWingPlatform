package com.easywing.platform.ai.session;

import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface SessionManager {
    Mono<ConversationSession> createSession(String userId, String sessionName);

    Mono<Optional<ConversationSession>> getSession(String sessionId);

    Mono<List<ConversationSession>> getUserSessions(String userId);

    /**
     * 列出指定用户在指定租户下的会话。
     * <p>
     * 默认实现复用 {@link #getUserSessions(String)} 后按 {@link SessionOwnership} 过滤，
     * 避免调用方遗漏租户条件导致跨租户列举会话。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID（空按 default 处理）
     */
    default Mono<List<ConversationSession>> getUserSessions(String userId, String tenantId) {
        return getUserSessions(userId).map(sessions -> sessions.stream()
                .filter(session -> SessionOwnership.isOwned(session, userId, tenantId))
                .toList());
    }

    Mono<ConversationSession> updateSession(ConversationSession session);

    Mono<Void> deleteSession(String sessionId);
}
