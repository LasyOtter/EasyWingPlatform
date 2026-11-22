package com.easywing.platform.ai.session;

import com.easywing.platform.ai.model.ChatMessage;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 会话共享链接：创建/列举/撤销/只读读取，并对读取做固定窗口限流。
 * <p>
 * 安全约束（PRD 5.2）：链接默认关闭；过期、撤销、访问次数耗尽一律返回 404，
 * 不泄露资源是否存在；只读视图不暴露创建者身份、消息元数据等内部字段。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class ShareLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final long ONE_MINUTE_MILLIS = 60_000L;

    private final SessionManager sessionManager;

    private final ShareLinkStore shareLinkStore;

    private final ShareLinkProperties properties;

    /** 固定窗口限流：key = 共享令牌，value = {窗口起点, 已用次数}。 */
    private final ConcurrentHashMap<String, long[]> rateWindows = new ConcurrentHashMap<>();

    public ShareLinkService(
            SessionManager sessionManager, ShareLinkStore shareLinkStore, ShareLinkProperties properties) {
        this.sessionManager = sessionManager;
        this.shareLinkStore = shareLinkStore;
        this.properties = properties;
    }

    /** 创建共享链接；会话必须属于当前用户与租户。 */
    public Mono<ShareLink> create(
            String sessionId, String userId, String tenantId, Integer ttlHours, Integer maxAccesses) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "共享链接未启用：easywing.ai.share.enabled=false");
        }
        int ttl = ttlHours == null ? properties.getDefaultTtlHours() : ttlHours;
        if (ttl < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttlHours 不能为负数");
        }
        if (ttl > properties.getMaxTtlHours()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttlHours 超过上限 " + properties.getMaxTtlHours());
        }
        int accesses = maxAccesses == null ? 0 : maxAccesses;
        if (accesses < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxAccesses 不能为负数");
        }
        return ownedSession(sessionId, userId, tenantId).flatMap(session -> {
            long existing = shareLinkStore.listBySession(sessionId).stream()
                    .filter(link -> link.isUsable(System.currentTimeMillis()))
                    .count();
            if (existing >= properties.getMaxLinksPerSession()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "会话共享链接已达上限 " + properties.getMaxLinksPerSession());
            }
            long now = System.currentTimeMillis();
            ShareLink link = ShareLink.builder()
                    .id(newToken())
                    .sessionId(sessionId)
                    .userId(userId)
                    .tenantId(SessionOwnership.normalizeTenant(tenantId))
                    .createdAt(now)
                    .expiresAt(ttl > 0 ? now + ttl * 3_600_000L : 0L)
                    .revoked(false)
                    .maxAccesses(accesses)
                    .accessCount(0)
                    .lastAccessAt(0L)
                    .build();
            shareLinkStore.save(link);
            return Mono.just(link);
        });
    }

    /** 列出某会话的共享链接。 */
    public Mono<List<ShareLink>> listBySession(String sessionId, String userId, String tenantId) {
        return ownedSession(sessionId, userId, tenantId).then(Mono.just(shareLinkStore.listBySession(sessionId)));
    }

    /** 撤销共享链接；仅创建者可撤销。 */
    public Mono<Void> revoke(String shareId, String userId, String tenantId) {
        return Mono.justOrEmpty(shareLinkStore.findById(shareId))
                .filter(link -> userId.equals(link.getUserId())
                        && SessionOwnership.normalizeTenant(tenantId)
                                .equals(SessionOwnership.normalizeTenant(link.getTenantId())))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "共享链接不存在")))
                .doOnNext(link -> shareLinkStore.delete(link.getId()))
                .then();
    }

    /**
     * 通过共享令牌读取会话只读视图。公开接口：令牌即凭据，过期/撤销/次数耗尽返回 404。
     */
    public Mono<SharedSessionView> getShared(String token) {
        if (!StringUtils.hasText(token)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "共享链接不存在"));
        }
        if (!acquire(token)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "访问过于频繁，请稍后再试");
        }
        ShareLink link = shareLinkStore.findById(token);
        long now = System.currentTimeMillis();
        if (link == null || !link.isUsable(now)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "共享链接不存在"));
        }
        return sessionManager
                .getSession(link.getSessionId())
                .flatMap(Mono::justOrEmpty)
                .filter(session -> SessionOwnership.normalizeTenant(session.getTenantId())
                        .equals(SessionOwnership.normalizeTenant(link.getTenantId())))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "共享链接不存在")))
                .map(session -> {
                    link.setAccessCount(link.getAccessCount() + 1);
                    link.setLastAccessAt(now);
                    shareLinkStore.save(link);
                    return toView(link, session);
                });
    }

    /** 共享会话的只读视图，刻意不含 userId / 消息元数据。 */
    public record SharedSessionView(
            String token,
            String sessionId,
            String sessionName,
            String model,
            String provider,
            long createdAt,
            long expiresAt,
            List<SharedMessage> messages) {}

    public record SharedMessage(String role, String content) {}

    private SharedSessionView toView(ShareLink link, ConversationSession session) {
        List<SharedMessage> messages = new ArrayList<>();
        if (session.getMessages() != null) {
            List<ChatMessage> all = session.getMessages();
            int limit = Math.max(0, all.size() - properties.getMaxMessages());
            for (int i = limit; i < all.size(); i++) {
                ChatMessage message = all.get(i);
                if (message == null) {
                    continue;
                }
                messages.add(new SharedMessage(message.getRole(), message.getContent()));
            }
        }
        return new SharedSessionView(
                link.getId(),
                session.getId(),
                session.getName(),
                session.getModel(),
                session.getProvider(),
                session.getCreatedAt(),
                link.getExpiresAt(),
                messages);
    }

    private Mono<ConversationSession> ownedSession(String sessionId, String userId, String tenantId) {
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> SessionOwnership.isOwned(session, userId, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在")));
    }

    /** 固定窗口限流：每令牌每分钟 {@code rateLimitPerMinute} 次。 */
    private boolean acquire(String token) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        long windowStart = System.currentTimeMillis() / ONE_MINUTE_MILLIS;
        long[] window = rateWindows.computeIfAbsent(token, key -> new long[] {windowStart, 0L});
        synchronized (window) {
            if (window[0] != windowStart) {
                window[0] = windowStart;
                window[1] = 0L;
            }
            if (window[1] >= limit) {
                return false;
            }
            window[1]++;
            return true;
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
