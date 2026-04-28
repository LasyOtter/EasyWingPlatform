package com.easywing.platform.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "easywing.ai")
public class AiProperties {

    private boolean enabled = true;

    private Map<String, ProviderConfig> providers = new HashMap<>();

    private MemoryConfig memory = new MemoryConfig();

    private SessionConfig session = new SessionConfig();

    private CompressionConfig compression = new CompressionConfig();

    private GatewayConfig gateway = new GatewayConfig();

    private KnowledgeBaseConfig knowledgeBase = new KnowledgeBaseConfig();

    private AgentConfig agent = new AgentConfig();

    private SkillConfig skill = new SkillConfig();

    @Data
    public static class ProviderConfig {
        private boolean enabled = false;
        private String type;
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 4096;
        private int timeoutSeconds = 120;
        private Map<String, String> extraParams = new HashMap<>();
    }

    @Data
    public static class OpenAiConfig extends ProviderConfig {
    }

    @Data
    public static class AnthropicConfig extends ProviderConfig {
    }

    @Data
    public static class GoogleConfig extends ProviderConfig {
    }

    @Data
    public static class MemoryConfig {
        private boolean enabled = true;
        private String type = "redis";
        private int maxMessages = 100;
        private long ttlHours = 24;
        private int summaryThreshold = 20;
    }

    @Data
    public static class SessionConfig {
        private boolean enabled = true;
        private String storageType = "redis";
        private long defaultTtlSeconds = 86400;
        private int maxConversationsPerUser = 100;
    }

    @Data
    public static class CompressionConfig {
        private boolean enabled = true;
        private String strategy = "smart";
        private int targetTokens = 8000;
        private double compressionRatio = 0.5;
    }

    @Data
    public static class GatewayConfig {
        private boolean enabled = true;
        private int port = 8082;
        private String apiPrefix = "/api/v1/ai";
    }

    @Data
    public static class KnowledgeBaseConfig {
        private boolean enabled = false;
        private String vectorStore = "milvus";
        private String collectionName = "easywing_knowledge";
        private int topK = 5;
        private double similarityThreshold = 0.7;
    }

    @Data
    public static class AgentConfig {
        private int maxIterations = 10;
        private int maxToolCallsPerIteration = 5;
        private long toolCallTimeoutMs = 30000;
        private boolean streamingEnabled = true;
    }

    @Data
    public static class SkillConfig {
        private boolean enabled = true;
        private String scanPackage = "com.easywing.platform.ai.skill";
    }
}