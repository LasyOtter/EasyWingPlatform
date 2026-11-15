package com.easywing.platform.ai.memory;

import com.easywing.platform.ai.rag.DocumentChunk;
import com.easywing.platform.ai.rag.EmbeddingService;
import com.easywing.platform.ai.rag.VectorStore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆管理器 - 跨会话的知识积累
 */
@Slf4j
@Service
public class LongTermMemoryManager {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    private static final String COLLECTION_NAME = "long_term_memory";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public LongTermMemoryManager(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 保存到长期记忆
     */
    public Mono<String> save(LongTermMemory memory) {
        log.info("Saving long-term memory for user: {}, type: {}",
                memory.getUserId(), memory.getType());

        return embeddingService.embed(memory.getContent())
                .flatMap(embedding -> {
                    DocumentChunk chunk = DocumentChunk.builder()
                            .id(UUID.randomUUID().toString())
                            .documentId(memory.getUserId())
                            .title(memory.getTitle())
                            .content(memory.getContent())
                            .embedding(embedding)
                            .metadata(buildMetadata(memory))
                            .createdAt(System.currentTimeMillis())
                            .source(memory.getSource())
                            .tags(memory.getTags())
                            .build();

                    return vectorStore.addDocument(chunk);
                });
    }

    /**
     * 批量保存
     */
    public Mono<List<String>> saveBatch(List<LongTermMemory> memories) {
        return reactor.core.publisher.Flux.fromIterable(memories)
                .flatMap(this::save)
                .collectList();
    }

    /**
     * 检索相关记忆
     */
    public Mono<List<LongTermMemory>> retrieve(String userId, String query, int topK) {
        log.info("Retrieving memories for user: {}, query: {}", userId, query);

        return embeddingService.embed(query)
                .flatMap(embedding ->
                    vectorStore.searchWithFilter(embedding, topK,
                        Map.of("userId", userId))
                )
                .map(chunks -> chunks.stream()
                        .map(this::chunkToMemory)
                        .collect(Collectors.toList())
                );
    }

    /**
     * 按类型检索
     */
    public Mono<List<LongTermMemory>> retrieveByType(
            String userId,
            MemoryType type,
            int limit) {

        log.info("Retrieving memories by type for user: {}, type: {}", userId, type);

        // 使用一个通用查询来检索该类型的记忆
        String typeQuery = getTypeQuery(type);

        return embeddingService.embed(typeQuery)
                .flatMap(embedding ->
                    vectorStore.searchWithFilter(embedding, limit,
                        Map.of("userId", userId, "type", type.name()))
                )
                .map(chunks -> chunks.stream()
                        .map(this::chunkToMemory)
                        .collect(Collectors.toList())
                );
    }

    /**
     * 更新记忆重要性
     */
    public Mono<Void> updateImportance(String memoryId, int importance) {
        // 简化实现：重新保存（实际应该支持原地更新）
        log.info("Updating importance for memory: {}, new importance: {}", memoryId, importance);
        return Mono.empty();
    }

    /**
     * 删除记忆
     */
    public Mono<Void> delete(String userId, String memoryId) {
        log.info("Deleting memory: {} for user: {}", memoryId, userId);
        return vectorStore.deleteDocument(memoryId);
    }

    /**
     * 清除用户的所有记忆
     */
    public Mono<Void> clearUserMemories(String userId) {
        log.info("Clearing all memories for user: {}", userId);
        return vectorStore.deleteDocument(userId);
    }

    /**
     * 记忆摘要 - 定期整理和压缩记忆
     */
    public Mono<List<LongTermMemory>> summarizeMemories(
            String userId,
            LocalDateTime since) {

        log.info("Summarizing memories for user: {} since: {}", userId, since);

        // 简化实现：返回最重要的记忆
        return Mono.just(List.of());
    }

    /**
     * 智能记忆增强 - 从对话中提取值得记住的信息
     */
    public Mono<List<LongTermMemory>> extractMemoriesFromConversation(
            String userId,
            String conversationText) {

        log.info("Extracting memories from conversation for user: {}", userId);

        List<LongTermMemory> memories = new ArrayList<>();

        // 简化实现：使用规则提取
        // 实际应该使用 LLM 智能提取

        // 提取偏好
        if (conversationText.contains("我喜欢") || conversationText.contains("我偏好")) {
            memories.add(LongTermMemory.builder()
                    .userId(userId)
                    .type(MemoryType.PREFERENCE)
                    .title("用户偏好")
                    .content(conversationText)
                    .importance(80)
                    .source("conversation")
                    .build());
        }

        // 提取事实
        if (conversationText.contains("我的") || conversationText.contains("我是")) {
            memories.add(LongTermMemory.builder()
                    .userId(userId)
                    .type(MemoryType.FACT)
                    .title("用户信息")
                    .content(conversationText)
                    .importance(90)
                    .source("conversation")
                    .build());
        }

        return Mono.just(memories);
    }

    /**
     * 构建元数据
     */
    private Map<String, Object> buildMetadata(LongTermMemory memory) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", memory.getUserId());
        metadata.put("type", memory.getType().name());
        metadata.put("importance", memory.getImportance());
        metadata.put("category", memory.getCategory());
        metadata.put("createdAt", LocalDateTime.now().format(DATE_FORMATTER));

        if (memory.getExpiresAt() != null) {
            metadata.put("expiresAt", memory.getExpiresAt().format(DATE_FORMATTER));
        }

        return metadata;
    }

    /**
     * 将文档块转换为记忆
     */
    private LongTermMemory chunkToMemory(DocumentChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();

        return LongTermMemory.builder()
                .id(chunk.getId())
                .userId(chunk.getDocumentId())
                .title(chunk.getTitle())
                .content(chunk.getContent())
                .type(MemoryType.valueOf((String) metadata.getOrDefault("type", "GENERAL")))
                .importance(((Number) metadata.getOrDefault("importance", 50)).intValue())
                .category((String) metadata.get("category"))
                .source(chunk.getSource())
                .tags(chunk.getTags())
                .score(chunk.getScore())
                .build();
    }

    /**
     * 获取类型查询词
     */
    private String getTypeQuery(MemoryType type) {
        return switch (type) {
            case PREFERENCE -> "用户偏好和喜好";
            case FACT -> "用户基本信息和事实";
            case EXPERIENCE -> "用户经历和体验";
            case SKILL -> "用户技能和专长";
            case GOAL -> "用户目标和计划";
            case RELATIONSHIP -> "人际关系";
            default -> "一般信息";
        };
    }

    /**
     * 长期记忆
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LongTermMemory {
        /**
         * 记忆 ID
         */
        private String id;

        /**
         * 用户 ID
         */
        private String userId;

        /**
         * 记忆类型
         */
        private MemoryType type;

        /**
         * 标题
         */
        private String title;

        /**
         * 内容
         */
        private String content;

        /**
         * 重要性 (0-100)
         */
        @Builder.Default
        private int importance = 50;

        /**
         * 分类
         */
        private String category;

        /**
         * 来源
         */
        private String source;

        /**
         * 标签
         */
        private List<String> tags;

        /**
         * 创建时间
         */
        private LocalDateTime createdAt;

        /**
         * 过期时间（可选）
         */
        private LocalDateTime expiresAt;

        /**
         * 相似度分数（检索时使用）
         */
        private double score;
    }

    /**
     * 记忆类型
     */
    public enum MemoryType {
        /**
         * 用户偏好（喜好、习惯）
         */
        PREFERENCE,

        /**
         * 事实信息（姓名、职业、基本信息）
         */
        FACT,

        /**
         * 经历体验（过去的事件、经历）
         */
        EXPERIENCE,

        /**
         * 技能专长
         */
        SKILL,

        /**
         * 目标计划
         */
        GOAL,

        /**
         * 人际关系
         */
        RELATIONSHIP,

        /**
         * 一般信息
         */
        GENERAL
    }
}
