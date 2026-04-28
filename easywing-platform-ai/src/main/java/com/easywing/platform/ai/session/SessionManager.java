package com.easywing.platform.ai.session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public interface SessionManager {
    Mono<ConversationSession> createSession(String userId, String sessionName);
    Mono<Optional<ConversationSession>> getSession(String sessionId);
    Mono<List<ConversationSession>> getUserSessions(String userId);
    Mono<ConversationSession> updateSession(ConversationSession session);
    Mono<Void> deleteSession(String sessionId);
}