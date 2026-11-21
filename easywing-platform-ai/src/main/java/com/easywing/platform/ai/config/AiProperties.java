package com.easywing.platform.ai.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    private EmbeddingConfig embedding = new EmbeddingConfig();

    private SandboxSettings sandbox = new SandboxSettings();

    private AgentConfig agent = new AgentConfig();

    private SkillConfig skill = new SkillConfig();

    private WebSocket websocket = new WebSocket();

    private EmbeddingConfig embedding = new EmbeddingConfig();

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
    @EqualsAndHashCode(callSuper = true)
    public static class OpenAiConfig extends ProviderConfig {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AnthropicConfig extends ProviderConfig {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class GoogleConfig extends ProviderConfig {}

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
    public static class EmbeddingConfig {

        /**
         * 提供方名称，对应 providers 中的 key，默认 openai。
         * 也兼容 DashScope 等 OpenAI 兼容端点。
         */
        private String provider = "openai";

        private String model = "text-embedding-3-small";

        /**
         * 覆盖提供方的 apiKey；为空时使用 providers 中的配置。
         */
        private String apiKey;

        /**
         * 覆盖提供方的 baseUrl；为空时使用 providers 中的配置。
         */
        private String baseUrl;

        /**
         * 单批最大输入文本数，OpenAI 建议不超过 2048。
         */
        private int batchSize = 32;

        /**
         * 向量维度，需与模型输出一致，默认 1536。
         */
        private int dimension = 1536;
    }

    @Data
    public static class SandboxSettings {

        /**
         * 执行模式：process（本机进程，仅开发）或 docker（容器隔离，生产推荐）。
         */
        private String mode = "process";

        private long maxExecutionTime = 10000;

        private int maxMemoryMB = 512;

        private int maxOutputSize = 10240;

        private boolean allowNetworkAccess = false;

        private boolean allowFileSystemAccess = false;
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

    @Data
    public static class WebSocket {
        /**
         * 握手时是否校验 JWT。Spring Security 不拦截 WebSocket 握手，需独立校验。
         * <p>
         * 仅当 AI 服务部署在网关之后、且网关已完成 JWT 校验时可关闭。生产环境不应关闭。
         */
        private boolean requireToken = true;

        /**
         * 允许的跨域来源，生产环境应配置为实际前端域名。
         */
        private List<String> allowedOrigins = List.of("http://localhost:8080");
    }

    /**
     * Embedding 配置。复用 openai provider 的 apiKey / baseUrl 调用 embeddings 接口。
     */
    @Data
    public static class EmbeddingConfig {
        /**
         * embedding 模型，默认 OpenAI text-embedding-3-small。
         */
        private String model = "text-embedding-3-small";
    }
}
