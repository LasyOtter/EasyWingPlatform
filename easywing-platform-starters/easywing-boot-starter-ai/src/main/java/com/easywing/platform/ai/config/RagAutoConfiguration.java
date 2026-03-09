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
package com.easywing.platform.ai.config;

import com.easywing.platform.ai.rag.RagService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "easywing.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Creating InMemoryEmbeddingStore");
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentSplitter documentSplitter(AiProperties properties) {
        AiProperties.Rag.DocumentLoader config = properties.getRag().getDocumentLoader();
        int chunkSize = config.getChunkSize() != null && config.getChunkSize() > 0 ? config.getChunkSize() : 1000;
        int chunkOverlap = config.getChunkOverlap() != null && config.getChunkOverlap() > 0 ? config.getChunkOverlap() : 200;
        return new DocumentByParagraphSplitter(chunkSize, chunkOverlap);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore,
                                  DocumentSplitter documentSplitter,
                                  AiProperties properties) {
        log.info("Creating RagService");
        return new RagService(embeddingModel, embeddingStore, documentSplitter, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.ai.rag.document-loader", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public List<Document> documents(AiProperties properties) {
        List<Document> documents = new ArrayList<>();
        List<String> paths = properties.getRag().getDocumentLoader().getResourcePaths();

        if (paths == null || paths.isEmpty()) {
            log.info("No document paths configured, skipping document loading");
            return documents;
        }

        for (String path : paths) {
            try {
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    log.info("Would load document from URL: {}", path);
                } else {
                    File file = new File(path);
                    if (file.exists()) {
                        log.info("Would load document from: {}", path);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load document from: {}", path, e);
            }
        }

        log.info("Documents loaded: {}", documents.size());
        return documents;
    }
}