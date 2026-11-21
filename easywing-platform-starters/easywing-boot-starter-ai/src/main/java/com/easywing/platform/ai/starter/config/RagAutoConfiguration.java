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
package com.easywing.platform.ai.starter.config;

import com.easywing.platform.ai.starter.rag.RagService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        int chunkOverlap =
                config.getChunkOverlap() != null && config.getChunkOverlap() > 0 ? config.getChunkOverlap() : 200;
        return new DocumentByParagraphSplitter(chunkSize, chunkOverlap);
    }

    /**
     * RAG 服务。
     *
     * <p>{@code EmbeddingModel} 是可选依赖：当所有 Embedding 供应商（openai/qwen/local）都未配置时，
     * {@link EmbeddingModelAutoConfiguration} 不会注册任何候选 Bean。此处用 {@link ObjectProvider} 兜底，
     * 保证 RAG 相关的基础设施（向量库、文档切分）在纯检索场景下仍可装配，
     * 仅在真正调用嵌入接口时才失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            EmbeddingStore<TextSegment> embeddingStore,
            DocumentSplitter documentSplitter,
            AiProperties properties) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            log.warn("未配置任何 EmbeddingModel（openai/qwen/local 均未启用），RagService 已降级创建，向量检索功能不可用");
        }
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
