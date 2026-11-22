package com.easywing.platform.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easywing.platform.ai.model.ChatCompletionResponse;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * {@link RagService} 归属过滤与元数据透传测试。
 */
class RagServiceTest {

    private VectorStore vectorStore;
    private EmbeddingService embeddingService;
    private AiProviderRegistry providerRegistry;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        embeddingService = mock(EmbeddingService.class);
        providerRegistry = mock(AiProviderRegistry.class);
        ragService = new RagService(vectorStore, embeddingService, providerRegistry);
    }

    @Test
    @DisplayName("入库时把用户与租户写入向量块 metadata")
    void addDocumentsWritesOwnershipMetadata() {
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[] {1f}));
        when(vectorStore.addDocument(any(DocumentChunk.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        RagService.Document doc = RagService.Document.builder()
                .id("doc-1")
                .title("标题")
                .content("内容")
                .source("upload.txt")
                .userId("user-1")
                .tenantId("tenant-a")
                .build();

        List<String> ids = ragService.addDocuments(List.of(doc)).block();

        assertThat(ids).isNotEmpty();
        ArgumentCaptor<DocumentChunk> captor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(vectorStore).addDocument(captor.capture());
        assertThat(captor.getValue().getMetadata())
                .containsEntry("userId", "user-1")
                .containsEntry("tenantId", "tenant-a");
    }

    @Test
    @DisplayName("检索时叠加用户与租户归属过滤")
    void queryAppliesOwnershipFilter() {
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[] {1f}));
        when(vectorStore.searchWithFilter(any(), anyInt(), any())).thenReturn(Mono.just(List.of()));
        when(providerRegistry.chat(any()))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .message(ChatMessage.builder()
                                        .role("assistant")
                                        .content("无上下文回答")
                                        .build())
                                .build()))
                        .build()));

        ragService
                .query(RagService.RagRequest.builder()
                        .query("问题")
                        .userId("user-1")
                        .tenantId("tenant-a")
                        .build())
                .block();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).searchWithFilter(any(), anyInt(), captor.capture());
        assertThat(captor.getValue()).containsEntry("userId", "user-1").containsEntry("tenantId", "tenant-a");
    }

    @Test
    @DisplayName("无归属时仅使用请求自带过滤条件")
    void queryWithoutOwnershipKeepsRequestFilter() {
        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[] {1f}));
        when(vectorStore.searchWithFilter(any(), anyInt(), any())).thenReturn(Mono.just(List.of()));
        when(providerRegistry.chat(any()))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .message(ChatMessage.builder()
                                        .role("assistant")
                                        .content("ok")
                                        .build())
                                .build()))
                        .build()));

        ragService
                .query(RagService.RagRequest.builder()
                        .query("问题")
                        .filter(Map.of("tag", "public"))
                        .build())
                .block();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).searchWithFilter(any(), anyInt(), captor.capture());
        assertThat(captor.getValue()).containsEntry("tag", "public");
        assertThat(captor.getValue()).doesNotContainKey("userId");
    }
}
