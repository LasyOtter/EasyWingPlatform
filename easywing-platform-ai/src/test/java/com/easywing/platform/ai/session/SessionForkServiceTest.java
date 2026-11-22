package com.easywing.platform.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SessionForkServiceTest {

    private InMemorySessionManager sessionManager;
    private SessionForkService forkService;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
        forkService = new SessionForkService(sessionManager);
    }

    private ConversationSession seedSession(String userId, String... contents) {
        ConversationSession session =
                sessionManager.createSession(userId, "main").block();
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            messages.add(ChatMessage.builder()
                    .role(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT)
                    .content(contents[i])
                    .build());
        }
        session.setMessages(messages);
        session.setMessageCount(messages.size());
        return sessionManager.updateSession(session).block();
    }

    private ConversationSession append(ConversationSession session, String content) {
        List<ChatMessage> messages = new ArrayList<>(session.getMessages() == null ? List.of() : session.getMessages());
        messages.add(ChatMessage.builder()
                .role(ChatMessage.ROLE_ASSISTANT)
                .content(content)
                .build());
        session.setMessages(messages);
        session.setMessageCount(messages.size());
        return sessionManager.updateSession(session).block();
    }

    @Test
    void forkCopiesMessagesUpToForkPointAndRecordsOrigin() {
        ConversationSession source = seedSession("u1", "q1", "a1", "q2", "a2");

        ConversationSession forked =
                forkService.fork(source.getId(), "u1", "default", 2, "retry").block();

        assertThat(forked).isNotNull();
        assertThat(forked.getParentSessionId()).isEqualTo(source.getId());
        assertThat(forked.getForkedAtMessageIndex()).isEqualTo(2);
        assertThat(forked.getMessages()).hasSize(3);
        assertThat(forked.getMessages()).extracting(ChatMessage::getContent).containsExactly("q1", "a1", "q2");
        assertThat(forked.getUserId()).isEqualTo("u1");
        assertThat(forked.getTenantId()).isEqualTo("default");
        assertThat(forked.getName()).isEqualTo("retry");
        assertThat(source.getMessages()).hasSize(4);
    }

    @Test
    void forkWithoutForkPointCopiesWholeConversation() {
        ConversationSession source = seedSession("u1", "q1", "a1", "q2", "a2");

        ConversationSession forked =
                forkService.fork(source.getId(), "u1", "default", null, null).block();

        assertThat(forked).isNotNull();
        assertThat(forked.getForkedAtMessageIndex()).isEqualTo(3);
        assertThat(forked.getMessages()).hasSize(4);
        assertThat(forked.getName()).startsWith("main");
    }

    @Test
    void forkClampsOutOfRangeForkPointToLastMessage() {
        ConversationSession source = seedSession("u1", "q1", "a1");

        ConversationSession forked =
                forkService.fork(source.getId(), "u1", "default", 99, "clamped").block();

        assertThat(forked).isNotNull();
        assertThat(forked.getMessages()).hasSize(2);
        assertThat(forked.getForkedAtMessageIndex()).isEqualTo(1);
    }

    @Test
    void forkFromAnotherUserIsRejected() {
        ConversationSession source = seedSession("u1", "q1", "a1");

        assertThatThrownBy(() -> forkService
                        .fork(source.getId(), "u2", "default", 1, "steal")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void branchesListsOnlyDirectChildrenOfSameTenant() {
        ConversationSession source = seedSession("u1", "q1", "a1", "q2", "a2");
        ConversationSession other = seedSession("u1", "x1", "y1");

        ConversationSession child1 =
                forkService.fork(source.getId(), "u1", "default", 1, "b1").block();
        ConversationSession child2 =
                forkService.fork(source.getId(), "u1", "default", 3, "b2").block();

        List<ConversationSession> branches =
                forkService.branches(source.getId(), "u1", "default").block();
        assertThat(branches).isNotNull();
        assertThat(branches)
                .extracting(ConversationSession::getId)
                .containsExactlyInAnyOrder(child1.getId(), child2.getId());
        assertThat(branches).extracting(ConversationSession::getParentSessionId).containsOnly(source.getId());

        List<ConversationSession> otherBranches =
                forkService.branches(other.getId(), "u1", "default").block();
        assertThat(otherBranches).isEmpty();
    }

    @Test
    void branchesDoesNotLeakAcrossUsers() {
        ConversationSession source = seedSession("u1", "q1", "a1");
        forkService.fork(source.getId(), "u1", "default", 1, "b1").block();

        List<ConversationSession> branches =
                forkService.branches(source.getId(), "u2", "default").block();
        assertThat(branches).isEmpty();
    }

    @Test
    void diffClassifiesUnchangedEditedAndAddedMessages() {
        ConversationSession source = seedSession("u1", "q1", "a1", "q2", "a2");
        ConversationSession target =
                forkService.fork(source.getId(), "u1", "default", 3, "branch").block();
        target.getMessages().get(1).setContent("a1-edited");
        target = append(target, "a3");

        BranchDiff diff = forkService
                .diff(source.getId(), target.getId(), "u1", "default")
                .block();

        assertThat(diff).isNotNull();
        assertThat(diff.getSourceMessageCount()).isEqualTo(4);
        assertThat(diff.getTargetMessageCount()).isEqualTo(5);
        assertThat(diff.getUnchangedCount()).isEqualTo(3);
        assertThat(diff.getEditedCount()).isEqualTo(1);
        assertThat(diff.getAddedCount()).isEqualTo(1);
        assertThat(diff.getRemovedCount()).isEqualTo(0);
        assertThat(diff.getEntries()).hasSize(5);
        assertThat(diff.getEntries().get(1).getType()).isEqualTo(BranchDiff.EDITED);
        assertThat(diff.getEntries().get(4).getType()).isEqualTo(BranchDiff.ADDED);
        assertThat(diff.getEntries().get(4).getTarget().getContent()).isEqualTo("a3");
        assertThat(diff.getEntries().get(4).getSource()).isNull();
    }

    @Test
    void diffDetectsRemovedMessagesWhenTargetIsShorter() {
        ConversationSession source = seedSession("u1", "q1", "a1", "q2", "a2");
        ConversationSession target =
                forkService.fork(source.getId(), "u1", "default", 1, "short").block();

        BranchDiff diff = forkService
                .diff(source.getId(), target.getId(), "u1", "default")
                .block();

        assertThat(diff).isNotNull();
        assertThat(diff.getRemovedCount()).isEqualTo(2);
        assertThat(diff.getAddedCount()).isZero();
        assertThat(diff.getEntries().get(2).getType()).isEqualTo(BranchDiff.REMOVED);
        assertThat(diff.getEntries().get(2).getSource().getContent()).isEqualTo("q2");
    }

    @Test
    void diffBetweenDifferentUsersIsRejected() {
        ConversationSession source = seedSession("u1", "q1", "a1");
        ConversationSession other = seedSession("u2", "x1", "y1");

        assertThatThrownBy(() -> forkService
                        .diff(source.getId(), other.getId(), "u1", "default")
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
