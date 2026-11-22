package com.easywing.platform.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 文件对话服务 - 上传、解析文本文件并写入知识库。
 * <p>
 * 仅接受白名单内的文本类扩展名（PDF/Word 等二进制格式需要专门解析库，当前明确拒绝并提示）。
 * 单文件上限 5MB、解码后文本上限 100 万字符；文件名仅取 basename，避免路径穿越。
 * 文档归属到用户与租户，检索与删除均按归属过滤。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FileDocumentService {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int MAX_CONTENT_CHARS = 1_000_000;

    /** 可直接按 UTF-8 读取的文本类扩展名 */
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt",
            "md",
            "markdown",
            "rst",
            "csv",
            "tsv",
            "json",
            "jsonl",
            "xml",
            "yaml",
            "yml",
            "html",
            "htm",
            "log",
            "properties",
            "conf",
            "ini",
            "sql",
            "sh",
            "bat",
            "ps1",
            "java",
            "kt",
            "kts",
            "py",
            "js",
            "jsx",
            "ts",
            "tsx",
            "css",
            "scss",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "cs",
            "php",
            "rb",
            "tex");

    private final RagService ragService;
    private final DocumentStore documentStore;

    public FileDocumentService(RagService ragService, DocumentStore documentStore) {
        this.ragService = ragService;
        this.documentStore = documentStore;
    }

    /**
     * 上传并解析一个文本文件，写入知识库后返回目录记录。
     */
    public Mono<DocumentRecord> upload(String userId, String tenantId, MultipartFile file, String title) {
        String fileName = sanitizeFileName(file == null ? null : file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw badRequest("文件为空或缺少文件");
        }
        if (fileName.isBlank()) {
            throw badRequest("缺少文件名");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("文件大小超过 5MB 上限");
        }
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw badRequest("不支持的文件类型: " + (extension.isEmpty() ? fileName : "." + extension));
        }

        String docTitle = title != null && !title.isBlank() ? title.trim() : fileName;

        return Mono.fromCallable(() -> file.getBytes())
                .onErrorMap(IOException.class, ex -> badRequest("读取文件失败: " + ex.getMessage()))
                .flatMap(bytes -> {
                    if (bytes.length > MAX_FILE_SIZE) {
                        throw badRequest("文件大小超过 5MB 上限");
                    }
                    String text = decodeText(bytes);
                    if (text.isBlank()) {
                        throw badRequest("文件内容为空");
                    }

                    RagService.Document document = RagService.Document.builder()
                            .id(UUID.randomUUID().toString())
                            .title(docTitle)
                            .content(text)
                            .source(fileName)
                            .userId(userId)
                            .tenantId(normalizeTenant(tenantId))
                            .build();

                    return ragService.addDocuments(List.of(document)).flatMap(chunkIds -> {
                        DocumentRecord record = DocumentRecord.builder()
                                .id(document.getId())
                                .userId(userId)
                                .tenantId(normalizeTenant(tenantId))
                                .title(docTitle)
                                .fileName(fileName)
                                .source(fileName)
                                .contentType(file.getContentType())
                                .sizeBytes(bytes.length)
                                .chunkCount(chunkIds.size())
                                .createdAt(System.currentTimeMillis())
                                .build();
                        return documentStore.save(record);
                    });
                });
    }

    /**
     * 列出当前用户（含租户）已上传的文档。
     */
    public Mono<List<DocumentRecord>> list(String userId, String tenantId) {
        return documentStore.listByUser(userId, tenantId);
    }

    /**
     * 查看单个文档（需归属当前用户）。
     */
    public Mono<DocumentRecord> get(String documentId, String userId, String tenantId) {
        return ownedDocument(documentId, userId, tenantId);
    }

    /**
     * 删除文档：先校验归属，再删向量块与目录记录。
     */
    public Mono<Void> delete(String documentId, String userId, String tenantId) {
        return ownedDocument(documentId, userId, tenantId)
                .flatMap(record -> ragService.deleteDocument(documentId))
                .then(documentStore.delete(documentId));
    }

    private Mono<DocumentRecord> ownedDocument(String documentId, String userId, String tenantId) {
        return documentStore
                .findById(documentId)
                .filter(record -> userId != null && userId.equals(record.getUserId()))
                .filter(record -> normalizeTenant(record.getTenantId()).equals(normalizeTenant(tenantId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private static String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String cleaned = StringUtils.cleanPath(rawName);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private static String decodeText(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                throw badRequest("文件不是文本格式（包含二进制内容）");
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            throw badRequest("文件不是有效的 UTF-8 文本");
        }
        if (text.length() > MAX_CONTENT_CHARS) {
            throw badRequest("文本长度超过 100 万字符上限");
        }
        return text;
    }

    private static String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
