package com.easywing.platform.ai.session;

import com.easywing.platform.ai.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 会话导出：支持 JSON 与 Markdown 两种格式，导出前做归属校验（同租户 + 所有者）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class SessionExportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final String JSON_MEDIA_TYPE = "application/json";

    private static final String MARKDOWN_MEDIA_TYPE = "text/markdown;charset=UTF-8";

    private final SessionManager sessionManager;

    public SessionExportService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 导出会话。
     *
     * @param format {@code json} 或 {@code markdown}/{@code md}，其他取值返回 400
     */
    public Mono<ExportedSession> export(String sessionId, String userId, String tenantId, String format) {
        return ownedSession(sessionId, userId, tenantId).map(session -> {
            String normalized = format == null ? "json" : format.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "json" -> new ExportedSession(fileName(session, "json"), JSON_MEDIA_TYPE, toJson(session));
                case "markdown", "md" -> new ExportedSession(
                        fileName(session, "md"), MARKDOWN_MEDIA_TYPE, toMarkdown(session));
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的导出格式: " + format);
            };
        });
    }

    /** 导出结果：文件名、媒体类型与正文。 */
    public record ExportedSession(String fileName, String contentType, String body) {}

    private Mono<ConversationSession> ownedSession(String sessionId, String userId, String tenantId) {
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> SessionOwnership.isOwned(session, userId, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在")));
    }

    private String toJson(ConversationSession session) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "会话序列化失败", e);
        }
    }

    private String toMarkdown(ConversationSession session) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ")
                .append(StringUtils.hasText(session.getName()) ? session.getName() : session.getId())
                .append("\n\n");
        markdown.append("- 会话 ID: ").append(session.getId()).append('\n');
        markdown.append("- 用户: ").append(session.getUserId()).append('\n');
        markdown.append("- 租户: ")
                .append(SessionOwnership.normalizeTenant(session.getTenantId()))
                .append('\n');
        markdown.append("- 模型: ").append(nullToEmpty(session.getModel())).append('\n');
        markdown.append("- Provider: ")
                .append(nullToEmpty(session.getProvider()))
                .append('\n');
        markdown.append("- 创建时间: ").append(formatTime(session.getCreatedAt())).append('\n');
        markdown.append("- 最后更新: ").append(formatTime(session.getUpdatedAt())).append('\n');
        markdown.append("- 消息数: ").append(session.getMessageCount()).append("\n\n---\n\n");

        if (session.getMessages() != null) {
            for (ChatMessage message : session.getMessages()) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                markdown.append("## ").append(nullToEmpty(message.getRole())).append("\n\n");
                markdown.append(message.getContent()).append("\n\n");
            }
        }
        return markdown.toString();
    }

    private static String formatTime(long epochMillis) {
        return epochMillis > 0 ? DATE_TIME.format(Instant.ofEpochMilli(epochMillis)) : "-";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "-" : value;
    }

    /** 文件名：优先会话名，非法字符替换为下划线。 */
    private static String fileName(ConversationSession session, String extension) {
        String base = StringUtils.hasText(session.getName()) ? session.getName() : session.getId();
        String sanitized = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        return sanitized + "." + extension;
    }
}
