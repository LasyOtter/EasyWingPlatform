package com.easywing.platform.ai.artifact;

import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.session.ConversationSession;
import com.easywing.platform.ai.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Artifacts 服务：从会话消息提取 HTML/React/Mermaid 产物，支持列表、详情与 Mermaid 导出。
 * <p>
 * 会话级归属校验：仅会话所有者（同租户）可读取其产物。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class ArtifactService {

    private final SessionManager sessionManager;

    public ArtifactService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Mono<List<Artifact>> listArtifacts(String sessionId, String userId, String tenantId) {
        return ownedSession(sessionId, userId, tenantId).map(this::extractFrom);
    }

    public Mono<Artifact> getArtifact(String sessionId, String artifactId, String userId, String tenantId) {
        return ownedSession(sessionId, userId, tenantId).flatMap(session -> extractFrom(session).stream()
                .filter(artifact -> artifact.getId().equals(artifactId))
                .findFirst()
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "产物不存在"))));
    }

    /**
     * 导出 Mermaid 产物：SVG 以 {@code foreignObject} 内嵌 mermaid.js 渲染；PNG 需浏览器渲染，明确返回 400。
     */
    public Mono<String> exportMermaid(Artifact artifact, String format) {
        if (artifact.getType() != Artifact.Type.MERMAID) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅 Mermaid 产物支持导出"));
        }
        if ("png".equalsIgnoreCase(format)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG 导出需要浏览器渲染，当前仅支持 SVG"));
        }
        return Mono.just(mermaidSvg(artifact.getCode()));
    }

    private List<Artifact> extractFrom(ConversationSession session) {
        List<Artifact> artifacts = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();
        if (session.getMessages() != null) {
            for (ChatMessage message : session.getMessages()) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                for (ArtifactExtractor.Extracted extracted : ArtifactExtractor.extract(message.getContent())) {
                    int ordinal = index.getAndIncrement();
                    artifacts.add(Artifact.builder()
                            .id(extracted.type().name().toLowerCase() + "-" + ordinal)
                            .sessionId(session.getId())
                            .userId(session.getUserId())
                            .tenantId(normalizeTenant(session.getTenantId()))
                            .type(extracted.type())
                            .title(extracted.title())
                            .language(extracted.language())
                            .code(extracted.code())
                            .createdAt(session.getUpdatedAt() > 0 ? session.getUpdatedAt() : System.currentTimeMillis())
                            .build());
                }
            }
        }
        return artifacts;
    }

    private Mono<ConversationSession> ownedSession(String sessionId, String userId, String tenantId) {
        return sessionManager
                .getSession(sessionId)
                .flatMap(Mono::justOrEmpty)
                .filter(session -> userId != null && userId.equals(session.getUserId()))
                .filter(session -> normalizeTenant(session.getTenantId()).equals(normalizeTenant(tenantId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在")));
    }

    private static String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    /**
     * 生成自包含 SVG：用 foreignObject 承载 mermaid 渲染容器与 CDN 脚本。
     */
    static String mermaidSvg(String code) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"960\" height=\"640\" viewBox=\"0 0 960 640\">\n"
                + "  <foreignObject width=\"100%\" height=\"100%\">\n"
                + "    <div xmlns=\"http://www.w3.org/1999/xhtml\" style=\"width:100%;height:100%;background:#ffffff\">"
                + "<pre class=\"mermaid\">"
                + escapeXml(code)
                + "</pre>"
                + "<script src=\"https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js\"></script>"
                + "<script>mermaid.initialize({startOnLoad:true});</script>"
                + "</div>\n"
                + "  </foreignObject>\n"
                + "</svg>\n";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
