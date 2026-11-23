package com.easywing.platform.ai.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionManagerTenantIsolationTest {

    private InMemorySessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemorySessionManager();
    }

    private ConversationSession createSession(String userId, String tenantId) {
        ConversationSession session = manager.createSession(userId, "会话").block();
        session.setTenantId(tenantId);
        return manager.updateSession(session).block();
    }

    @Test
    void shouldOnlyReturnSessionsOfRequestedTenant() {
        ConversationSession tenantA = createSession("user-1", "tenant-a");
        createSession("user-1", "tenant-b");
        createSession("user-2", "tenant-a");

        var sessions = manager.getUserSessions("user-1", "tenant-a").block();

        assertThat(sessions).extracting(ConversationSession::getId).containsExactly(tenantA.getId());
    }

    @Test
    void shouldNormalizeBlankTenantToDefault() {
        ConversationSession legacy = createSession("user-1", null);
        createSession("user-1", "tenant-b");

        var sessions = manager.getUserSessions("user-1", "default").block();

        assertThat(sessions).extracting(ConversationSession::getId).containsExactly(legacy.getId());
    }

    @Test
    void shouldNotReturnOtherUsersSessions() {
        createSession("user-2", "tenant-a");

        var sessions = manager.getUserSessions("user-1", "tenant-a").block();

        assertThat(sessions).isEmpty();
    }
}
