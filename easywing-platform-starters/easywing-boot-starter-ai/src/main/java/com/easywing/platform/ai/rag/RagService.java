/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.ai.rag;

import com.easywing.platform.ai.config.AiProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final PromptTemplate RAG_PROMPT = PromptTemplate.from("""
        Based on the following information, answer the question.

        Information:
        {{information}}

        Question: {{question}}

        Answer:
        """);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final AiProperties properties;

    public RagService(EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      DocumentSplitter documentSplitter,
                      AiProperties properties) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentSplitter = documentSplitter;
        this.properties = properties;
    }

    public void ingestDocuments(List<Document> documents) {
        log.info("Ingesting {} documents", documents.size());

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(documentSplitter)
                .build();

        ingestor.ingest(documents);

        log.info("Successfully ingested {} documents", documents.size());
    }

    public List<TextSegment> search(String query, int topK) {
        var queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches().stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
    }

    public String searchAndFormat(String query, int topK) {
        List<TextSegment> segments = search(query, topK);

        StringBuilder context = new StringBuilder();
        context.append("Relevant information:\n\n");

        for (int i = 0; i < segments.size(); i++) {
            context.append(i + 1).append(". ")
                    .append(segments.get(i).text())
                    .append("\n\n");
        }

        return context.toString();
    }

    public String queryWithContext(String query, int topK) {
        String context = searchAndFormat(query, topK);
        return RAG_PROMPT.apply(Map.of(
                "information", context,
                "question", query
        )).text();
    }

    public Document createDocument(String content, Map<String, Object> metadata) {
        Metadata docMetadata = new Metadata();
        if (metadata != null) {
            metadata.forEach((key, value) -> docMetadata.put(key, String.valueOf(value)));
        }
        return Document.from(content, docMetadata);
    }

    public Document createDocument(String content) {
        return createDocument(content, null);
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }
}
