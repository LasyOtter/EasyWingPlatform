package com.easywing.platform.ai.rag;

import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.provider.AiProviderRegistry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * RAG 服务 - 检索增强生成
 */
@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final AiProviderRegistry providerRegistry;

    public RagService(VectorStore vectorStore, EmbeddingService embeddingService, AiProviderRegistry providerRegistry) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.providerRegistry = providerRegistry;
    }

    /**
     * RAG 查询 - 检索相关文档并生成答案
     */
    public Mono<RagResponse> query(RagRequest request) {
        log.info("RAG query: {}", request.getQuery());

        long startTime = System.currentTimeMillis();

        // 1. 生成查询的 embedding
        return embeddingService.embed(request.getQuery()).flatMap(queryEmbedding -> {
            // 2. 检索相关文档
            return vectorStore.search(queryEmbedding, request.getTopK()).flatMap(chunks -> {
                if (chunks.isEmpty()) {
                    log.warn("No relevant documents found");
                    return generateWithoutContext(request);
                }

                // 3. 构建带上下文的提示词
                String context = buildContext(chunks);
                String prompt = buildPrompt(request.getQuery(), context);

                // 4. 调用 LLM 生成答案
                ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                        .model(request.getModel() != null ? request.getModel() : "gpt-4o")
                        .messages(List.of(ChatMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()))
                        .temperature(request.getTemperature())
                        .build();

                return providerRegistry.chat(completionRequest).map(response -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    String answer = response.getChoices() != null
                                    && !response.getChoices().isEmpty()
                            ? response.getChoices().get(0).getMessage().getContent()
                            : "无法生成答案";

                    return RagResponse.builder()
                            .answer(answer)
                            .sources(chunks)
                            .retrievedCount(chunks.size())
                            .totalExecutionTimeMs(totalTime)
                            .build();
                });
            });
        });
    }

    /**
     * 添加文档到知识库
     */
    public Mono<List<String>> addDocuments(List<Document> documents) {
        log.info("Adding {} documents to knowledge base", documents.size());

        return reactor.core.publisher.Flux.fromIterable(documents)
                .flatMap(doc -> {
                    // 文档分块
                    List<String> chunks = chunkDocument(doc.getContent(), 500, 50);

                    return reactor.core.publisher.Flux.fromIterable(chunks)
                            .index()
                            .flatMap(tuple -> {
                                int index = tuple.getT1().intValue();
                                String chunkText = tuple.getT2();

                                // 生成 embedding
                                return embeddingService.embed(chunkText).map(embedding -> DocumentChunk.builder()
                                        .id(doc.getId() + "_chunk_" + index)
                                        .documentId(doc.getId())
                                        .title(doc.getTitle())
                                        .content(chunkText)
                                        .embedding(embedding)
                                        .metadata(doc.getMetadata())
                                        .chunkIndex(index)
                                        .createdAt(System.currentTimeMillis())
                                        .source(doc.getSource())
                                        .tags(doc.getTags())
                                        .build());
                            })
                            .flatMap(vectorStore::addDocument);
                })
                .collectList();
    }

    /**
     * 删除文档
     */
    public Mono<Void> deleteDocument(String documentId) {
        return vectorStore.deleteDocument(documentId);
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format(
                        "【来源: %s】\n%s",
                        chunk.getTitle() != null ? chunk.getTitle() : chunk.getSource(), chunk.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String query, String context) {
        return String.format(
                """
                你是一个智能助手。请根据以下上下文信息回答用户的问题。

                上下文信息：
                %s

                注意事项：
                1. 只根据上下文信息回答，不要编造内容
                2. 如果上下文中没有相关信息，请明确说明
                3. 回答要准确、简洁
                4. 可以引用具体的来源

                用户问题：%s

                请回答：
                """,
                context, query);
    }

    /**
     * 无上下文生成（fallback）
     */
    private Mono<RagResponse> generateWithoutContext(RagRequest request) {
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(request.getModel() != null ? request.getModel() : "gpt-4o")
                .messages(List.of(ChatMessage.builder()
                        .role("user")
                        .content(request.getQuery())
                        .build()))
                .temperature(request.getTemperature())
                .build();

        return providerRegistry.chat(completionRequest).map(response -> {
            String answer =
                    response.getChoices() != null && !response.getChoices().isEmpty()
                            ? response.getChoices().get(0).getMessage().getContent()
                            : "无法生成答案";

            return RagResponse.builder()
                    .answer(answer)
                    .sources(List.of())
                    .retrievedCount(0)
                    .build();
        });
    }

    /**
     * 文档分块
     */
    private List<String> chunkDocument(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }

        return chunks;
    }

    /**
     * 文档类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Document {
        private String id;
        private String title;

        @NotBlank
        private String content;

        private String source;
        private Map<String, Object> metadata;
        private List<String> tags;
    }

    /**
     * RAG 请求
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagRequest {
        @NotBlank
        private String query;

        @lombok.Builder.Default
        @Min(1)
        @Max(50)
        private int topK = 3;

        private String model;

        @lombok.Builder.Default
        private double temperature = 0.7;

        private Map<String, Object> filter;
    }

    /**
     * RAG 响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagResponse {
        private String answer;
        private List<DocumentChunk> sources;
        private int retrievedCount;
        private long totalExecutionTimeMs;
    }
}
