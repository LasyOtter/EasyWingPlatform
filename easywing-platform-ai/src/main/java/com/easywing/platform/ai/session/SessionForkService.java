package com.easywing.platform.ai.session;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 会话 Fork/分支服务
 * <p>
 * Fork 会复制源会话到指定消息下标（含）为止的全部消息，生成一个归属于同一用户与租户的新会话，
 * 并记录 {@code parentSessionId} 与 {@code forkedAtMessageIndex}。分支差异按消息下标逐位比较，
 * 让调用方能够看到两条分支从分叉点开始的具体差别。
 * <p>
 * 所有读写都经由 {@link SessionManager}，因此内存与 Redis 两种存储下语义一致。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class SessionForkService {

    private final SessionManager sessionManager;

    public SessionForkService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 从源会话 Fork 出一个新会话。
     *
     * @param sessionId 源会话 ID
     * @param userId 当前用户（用于归属校验）
     * @param tenantId 当前租户（用于归属校验）
     * @param forkPointIndex 保留到的消息下标（含）；为 null 表示复制全部消息
     * @param newName 新会话名称；为空时使用「源名称 (fork)」
     */
    public Mono<ConversationSession> fork(
            String sessionId, String userId, String tenantId, Integer forkPointIndex, String newName) {
        return ownedSession(sessionId, userId, tenantId).flatMap(source -> {
            List<ChatMessage> sourceMessages = source.getMessages() == null ? List.of() : source.getMessages();
            int limit = forkPointIndex == null
                    ? sourceMessages.size()
                    : Math.max(0, Math.min(forkPointIndex + 1, sourceMessages.size()));
            List<ChatMessage> copied = sourceMessages.subList(0, limit).stream()
                    .map(SessionForkService::copyMessage)
                    .toList();

            long now = System.currentTimeMillis();
            ConversationSession forked = ConversationSession.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .tenantId(normalizeTenant(tenantId))
                    .name(newName != null && !newName.isBlank() ? newName : source.getName() + " (fork)")
                    .model(source.getModel())
                    .provider(source.getProvider())
                    .messages(copied)
                    .messageCount(copied.size())
                    .createdAt(now)
                    .updatedAt(now)
                    .lastActiveAt(now)
                    .archived(false)
                    .parentSessionId(source.getId())
                    .forkedAtMessageIndex(limit > 0 ? limit - 1 : null)
                    .build();

            return sessionManager
                    .updateSession(forked)
                    .doOnSuccess(saved -> log.info(
                            "Forked session {} from {} at message index {}", saved.getId(), sessionId, limit - 1));
        });
    }

    /**
     * 列出指定会话的直接分支（含间接分支以外的直接子会话）。
     */
    public Mono<List<ConversationSession>> branches(String sessionId, String userId, String tenantId) {
        return sessionManager.getUserSessions(userId).map(sessions -> sessions.stream()
                .filter(session -> sessionId.equals(session.getParentSessionId()))
                .filter(session -> tenantMatches(session, tenantId))
                .sorted(Comparator.comparingLong(ConversationSession::getCreatedAt)
                        .reversed())
                .toList());
    }

    /**
     * 比较两个会话的消息差异。
     */
    public Mono<BranchDiff> diff(String sessionId, String otherSessionId, String userId, String tenantId) {
        return ownedSession(sessionId, userId, tenantId)
                .flatMap(source ->
                        ownedSession(otherSessionId, userId, tenantId).map(target -> buildDiff(source, target)));
    }

    private BranchDiff buildDiff(ConversationSession source, ConversationSession target) {
        List<ChatMessage> sourceMessages = source.getMessages() == null ? List.of() : source.getMessages();
        List<ChatMessage> targetMessages = target.getMessages() == null ? List.of() : target.getMessages();
        int size = Math.max(sourceMessages.size(), targetMessages.size());

        BranchDiff.BranchDiffBuilder builder = BranchDiff.builder()
                .sourceSessionId(source.getId())
                .targetSessionId(target.getId())
                .sourceMessageCount(sourceMessages.size())
                .targetMessageCount(targetMessages.size());

        int unchanged = 0;
        int edited = 0;
        int added = 0;
        int removed = 0;
        List<BranchDiff.DiffEntry> entries = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            ChatMessage sourceMessage = i < sourceMessages.size() ? sourceMessages.get(i) : null;
            ChatMessage targetMessage = i < targetMessages.size() ? targetMessages.get(i) : null;
            String type;
            if (sourceMessage != null && targetMessage != null) {
                boolean same = Comparator.comparing(ChatMessage::getRole, Comparator.nullsFirst(String::compareTo))
                                .thenComparing(ChatMessage::getContent, Comparator.nullsFirst(String::compareTo))
                                .compare(sourceMessage, targetMessage)
                        == 0;
                type = same ? BranchDiff.UNCHANGED : BranchDiff.EDITED;
            } else if (sourceMessage != null) {
                type = BranchDiff.REMOVED;
            } else {
                type = BranchDiff.ADDED;
            }

            switch (type) {
                case BranchDiff.UNCHANGED -> unchanged++;
                case BranchDiff.EDITED -> edited++;
                case BranchDiff.ADDED -> added++;
                default -> removed++;
            }
            entries.add(BranchDiff.DiffEntry.builder()
                    .index(i)
                    .type(type)
                    .source(sourceMessage)
                    .target(targetMessage)
                    .build());
        }

        return builder.unchangedCount(unchanged)
                .editedCount(edited)
                .addedCount(added)
                .removedCount(removed)
                .entries(entries)
                .build();
    }

    private Mono<ConversationSession> ownedSession(String sessionId, String userId, String tenantId) {
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> userId != null && userId.equals(session.getUserId()))
                .filter(session -> tenantMatches(session, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    /**
     * 复制消息对象，避免 Fork 出的会话与源会话共享可变对象导致互相污染。
     */
    private static ChatMessage copyMessage(ChatMessage message) {
        return ChatMessage.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .name(message.getName())
                .toolCallId(message.getToolCallId())
                .toolCalls(message.getToolCalls() == null ? null : new ArrayList<>(message.getToolCalls()))
                .toolCall(message.getToolCall())
                .metadata(message.getMetadata() == null ? null : new LinkedHashMap<>(message.getMetadata()))
                .contentParts(message.getContentParts() == null ? null : new ArrayList<>(message.getContentParts()))
                .thinking(message.getThinking())
                .build();
    }

    private boolean tenantMatches(ConversationSession session, String tenantId) {
        return normalizeTenant(session.getTenantId()).equals(normalizeTenant(tenantId));
    }

    private static String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }
}
