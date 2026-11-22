package com.easywing.platform.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * {@link FileDocumentService} 测试
 * <p>
 * 覆盖白名单校验、二进制拒绝、路径穿越清洗、归属隔离与级联删除。
 */
class FileDocumentServiceTest {

    private RagService ragService;
    private InMemoryDocumentStore documentStore;
    private FileDocumentService service;

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        documentStore = new InMemoryDocumentStore();
        service = new FileDocumentService(ragService, documentStore);
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/plain", content);
    }

    @Test
    @DisplayName("上传文本文件：写入知识库并携带用户/租户归属")
    void uploadCarriesOwnership() {
        when(ragService.addDocuments(anyList())).thenReturn(Mono.just(List.of("doc_chunk_0", "doc_chunk_1")));

        DocumentRecord record = service.upload(
                        "user-1", "tenant-a", file("notes.txt", "hello rag".getBytes(StandardCharsets.UTF_8)), "笔记")
                .block();

        assertThat(record).isNotNull();
        assertThat(record.getUserId()).isEqualTo("user-1");
        assertThat(record.getTenantId()).isEqualTo("tenant-a");
        assertThat(record.getFileName()).isEqualTo("notes.txt");
        assertThat(record.getTitle()).isEqualTo("笔记");
        assertThat(record.getChunkCount()).isEqualTo(2);
        assertThat(record.getSizeBytes()).isEqualTo("hello rag".length());

        ArgumentCaptor<List<RagService.Document>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(ragService).addDocuments(captor.capture());
        RagService.Document document = captor.getValue().get(0);
        assertThat(document.getUserId()).isEqualTo("user-1");
        assertThat(document.getTenantId()).isEqualTo("tenant-a");
        assertThat(document.getContent()).isEqualTo("hello rag");
    }

    @Test
    @DisplayName("拒绝白名单外的扩展名")
    void rejectsUnsupportedExtension() {
        MockMultipartFile exe = file("payload.exe", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> service.upload("user-1", "default", exe, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @DisplayName("拒绝空文件")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload("user-1", "default", file("empty.txt", new byte[0]), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("文件为空");
    }

    @Test
    @DisplayName("拒绝二进制内容")
    void rejectsBinaryContent() {
        MockMultipartFile binary = file("fake.txt", new byte[] {'h', 'i', 0, 'x'});
        assertThatThrownBy(
                        () -> service.upload("user-1", "default", binary, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("二进制");
    }

    @Test
    @DisplayName("文件名取 basename，避免路径穿越")
    void sanitizesFileName() {
        when(ragService.addDocuments(anyList())).thenReturn(Mono.just(List.of("doc_chunk_0")));

        DocumentRecord record = service.upload(
                        "user-1", "default", file("..\\..\\evil.txt", "content".getBytes(StandardCharsets.UTF_8)), null)
                .block();

        assertThat(record).isNotNull();
        assertThat(record.getFileName()).isEqualTo("evil.txt");
        assertThat(record.getFileName()).doesNotContain("..");
    }

    @Test
    @DisplayName("列表只返回当前用户的文档")
    void listsOnlyOwnDocuments() {
        seed("doc-a", "user-1", "default");
        seed("doc-b", "user-2", "default");

        List<DocumentRecord> own = service.list("user-1", "default").block();

        assertThat(own).hasSize(1);
        assertThat(own.get(0).getId()).isEqualTo("doc-a");
    }

    @Test
    @DisplayName("删除他人文档返回 404")
    void deleteRejectsForeignDocument() {
        seed("doc-a", "user-1", "default");

        assertThatThrownBy(() -> service.delete("doc-a", "user-2", "default").block())
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("删除本人文档：向量块与目录记录级联删除")
    void deleteCascades() {
        seed("doc-a", "user-1", "default");
        when(ragService.deleteDocument("doc-a")).thenReturn(Mono.empty());

        service.delete("doc-a", "user-1", "default").block();

        assertThat(documentStore.peek("doc-a")).isEmpty();
    }

    private void seed(String id, String userId, String tenantId) {
        DocumentRecord record = DocumentRecord.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .title(id)
                .fileName(id + ".txt")
                .sizeBytes(10)
                .createdAt(System.currentTimeMillis())
                .build();
        documentStore.save(record).block();
    }
}
