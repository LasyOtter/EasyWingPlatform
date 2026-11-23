package com.easywing.platform.ai.session;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 消息全文搜索：在用户自己的会话（同租户）内按关键字匹配会话名与消息内容。
 * <p>
 * 关键字大小写不敏感；命中按会话顺序返回，总数受 {@code easywing.ai.session-search.max-results} 限制。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class SessionSearchService {

    private final SessionManager sessionManager;
    private final AiProperties aiProperties;

    public SessionSearchService(SessionManager sessionManager, AiProperties aiProperties) {
        this.sessionManager = sessionManager;
        this.aiProperties = aiProperties;
    }

    /**
     * 搜索当前用户（同租户）的会话。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID（空按 default 处理）
     * @param query 关键字，空白时返回空列表
     * @param limit 命中上限，非正数时取配置默认值
     */
    public Mono<List<SessionSearchHit>> search(String userId, String tenantId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return Mono.just(List.of());
        }
        int maxResults = aiProperties.getSessionSearch().getMaxResults();
        int effectiveLimit = limit > 0 ? Math.min(limit, maxResults) : maxResults;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return sessionManager
                .getUserSessions(userId, tenantId)
                .map(sessions -> searchSessions(sessions, userId, tenantId, needle, effectiveLimit));
    }

    private List<SessionSearchHit> searchSessions(
            List<ConversationSession> sessions, String userId, String tenantId, String needle, int limit) {
        List<SessionSearchHit> hits = new ArrayList<>();
        for (ConversationSession session : sessions) {
            if (hits.size() >= limit) {
                break;
            }
            if (SessionOwnership.isOwned(session, userId, tenantId)) {
                collect(session, needle, limit, hits);
            }
        }
        return List.copyOf(hits);
    }

    private void collect(ConversationSession session, String needle, int limit, List<SessionSearchHit> hits) {
        if (StringUtils.hasText(session.getName())
                && session.getName().toLowerCase(Locale.ROOT).contains(needle)) {
            hits.add(hit(session, -1, "session", session.getName()));
        }
        if (session.getMessages() == null) {
            return;
        }
        int snippetLength = aiProperties.getSessionSearch().getSnippetLength();
        for (int i = 0; i < session.getMessages().size() && hits.size() < limit; i++) {
            ChatMessage message = session.getMessages().get(i);
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String content = message.getContent();
            if (content.toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(hit(session, i, message.getRole(), snippet(content, needle, snippetLength)));
            }
        }
    }

    private SessionSearchHit hit(ConversationSession session, int messageIndex, String role, String snippet) {
        return SessionSearchHit.builder()
                .sessionId(session.getId())
                .sessionName(session.getName())
                .userId(session.getUserId())
                .tenantId(SessionOwnership.normalizeTenant(session.getTenantId()))
                .messageIndex(messageIndex)
                .role(role)
                .snippet(snippet)
                .updatedAt(session.getLastActiveAt() > 0 ? session.getLastActiveAt() : session.getUpdatedAt())
                .build();
    }

    /**
     * 命中片段：以首个匹配位置为中心截取，两端补省略号。
     */
    static String snippet(String content, String lowercaseNeedle, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        int index = content.toLowerCase(Locale.ROOT).indexOf(lowercaseNeedle);
        if (index < 0) {
            return truncate(content, maxLength);
        }
        int half = Math.max(1, maxLength / 2);
        int start = Math.max(0, index - half);
        int end = Math.min(content.length(), index + lowercaseNeedle.length() + half);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < content.length() ? "…" : "";
        String window = content.substring(start, end).replace('\n', ' ');
        int budget = maxLength - prefix.length() - suffix.length();
        if (budget <= 0) {
            return (prefix + suffix).substring(0, maxLength);
        }
        if (window.length() > budget) {
            int localIndex = index - start;
            int from = Math.max(0, Math.min(localIndex - budget / 2, window.length() - budget));
            window = window.substring(from, from + budget);
        }
        return prefix + window + suffix;
    }

    private static String truncate(String text, int maxLength) {
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        if (maxLength == 1) {
            return "…";
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
