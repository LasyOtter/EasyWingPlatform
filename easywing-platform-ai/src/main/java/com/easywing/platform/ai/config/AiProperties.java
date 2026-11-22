package com.easywing.platform.ai.config;

import com.easywing.platform.ai.mcp.McpServerConfig;
import java.util.ArrayList;
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

    private Map<String, CustomEndpointConfig> customEndpoints = new HashMap<>();

    /**
     * Azure OpenAI 配置。
     * <p>
     * Azure 与公共 OpenAI 的鉴权头、请求路径、模型标识均不同，因此单独建模；
     * 默认关闭，凭据只经配置或环境变量注入，禁止硬编码。
     */
    private AzureOpenAiConfig azure = new AzureOpenAiConfig();

    /**
     * OpenAI Responses API 配置。
     * <p>
     * 与 Chat Completions 不同的官方协议（{@code POST /v1/responses}），默认关闭，
     * 凭据只经配置或环境变量注入，禁止硬编码。
     */
    private ResponsesApiConfig responses = new ResponsesApiConfig();

    /**
     * Vertex AI（Gemini on Vertex）配置。
     * <p>
     * 与 Google AI Studio 共用 Gemini 线协议，但使用 OAuth2 访问令牌鉴权；
     * 默认关闭，令牌只经配置或环境变量注入，禁止硬编码。
     */
    private VertexConfig vertex = new VertexConfig();

    /**
     * AWS Bedrock（Converse / ConverseStream）配置。
     * <p>
     * 使用 AWS SDK v2 官方客户端，SigV4 签名与 event-stream 解码由 SDK 负责；
     * 默认关闭，凭据只经配置或环境变量注入，禁止硬编码。
     */
    private BedrockConfig bedrock = new BedrockConfig();

    private MemoryConfig memory = new MemoryConfig();

    private SessionConfig session = new SessionConfig();

    private CompressionConfig compression = new CompressionConfig();

    private GatewayConfig gateway = new GatewayConfig();

    private KnowledgeBaseConfig knowledgeBase = new KnowledgeBaseConfig();

    private SandboxSettings sandbox = new SandboxSettings();

    private AgentConfig agent = new AgentConfig();

    private SkillConfig skill = new SkillConfig();

    private McpConfig mcp = new McpConfig();

    private WebSocket websocket = new WebSocket();

    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * 联网检索配置。
     * <p>
     * 由「搜索提供商 → 内容抓取器 → 结果重排器」三段组成，默认关闭；
     * 密钥只经配置或环境变量注入，禁止硬编码。
     */
    private WebSearchConfig webSearch = new WebSearchConfig();

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

    /**
     * Azure OpenAI 提供方配置。
     * <p>
     * {@code baseUrl} 为形如 {@code https://&lt;resource&gt;.openai.azure.com} 的资源根地址；
     * {@code deployment} 为模型部署名（请求体中的 {@code model} 字段）；
     * {@code models} 为对外暴露的逻辑模型名清单，留空时回退到 OpenAI 常用模型。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AzureOpenAiConfig extends OpenAiConfig {

        private String apiVersion = "2024-06-01";

        private String deployment;

        private List<String> models = new ArrayList<>();
    }

    /**
     * OpenAI Responses API 提供方配置。
     * <p>
     * {@code baseUrl} 为形如 {@code https://api.openai.com/v1} 的 API 根地址；
     * {@code models} 为对外暴露的逻辑模型名清单，留空时回退到 Responses API 常用模型。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ResponsesApiConfig extends ProviderConfig {

        private List<String> models = new ArrayList<>();
    }

    /**
     * Vertex AI 提供方配置。
     * <p>
     * {@code baseUrl} 需指向
     * {@code https://<region>-aiplatform.googleapis.com/v1/projects/<project>/locations/<region>/publishers/google/models}；
     * {@code accessToken} 为 OAuth2 访问令牌（服务账号令牌或用户令牌）；
     * {@code models} 为对外暴露的逻辑模型名清单，留空时回退到 Gemini 常用模型。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class VertexConfig extends ProviderConfig {

        private String accessToken;

        private List<String> models = new ArrayList<>();
    }

    /**
     * AWS Bedrock 提供方配置。
     * <p>
     * {@code region} 决定端点 {@code https://bedrock-runtime.<region>.amazonaws.com}；
     * {@code accessKey}/{@code secretKey} 为静态凭据（也可交由 SDK 默认凭据链解析，此时可留空）；
     * {@code endpoint} 用于覆盖端点（本地模拟或 VPC 内部地址）；
     * {@code models} 为对外暴露的逻辑模型名清单，留空时回退到常用 Bedrock 模型。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BedrockConfig extends ProviderConfig {

        private String region = "us-east-1";

        private String accessKey;

        private String secretKey;

        private String sessionToken;

        private String endpoint;

        private List<String> models = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AnthropicConfig extends ProviderConfig {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class GoogleConfig extends ProviderConfig {}

    @Data
    public static class CustomEndpointConfig {
        private boolean enabled = true;
        private String name;
        private String baseUrl;
        private String apiKey;
        private List<String> models = List.of();
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
    public static class McpConfig {
        /** MCP server 配置：名称 -> 传输配置（http 或 stdio）。 */
        private Map<String, McpServerConfig> servers = new HashMap<>();

        /** 兼容旧配置：server 名称 -> Bearer Token（仅 http 传输使用）。 */
        private Map<String, String> serverTokens = new HashMap<>();

        /**
         * 解析 server 配置并返回副本，避免调用方修改到共享配置；同时合并旧版 server-tokens 令牌。
         */
        public McpServerConfig resolve(String serverName) {
            McpServerConfig source = servers.get(serverName);
            if (source == null) {
                return null;
            }
            McpServerConfig resolved = source.toBuilder().build();
            resolved.setName(serverName);
            if ((resolved.getBearerToken() == null || resolved.getBearerToken().isBlank())
                    && serverTokens != null
                    && serverTokens.get(serverName) != null) {
                resolved.setBearerToken(serverTokens.get(serverName));
            }
            return resolved;
        }
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
     * 联网检索流水线配置。
     * <p>
     * 三段组件：{@code provider}（搜索提供商）、{@code fetcher}（内容抓取，内置 HTML 实现）、
     * {@code jina}（可选重排器）。默认全部关闭，密钥只经配置或环境变量注入。
     */
    @Data
    public static class WebSearchConfig {

        private boolean enabled = false;

        /** 搜索提供商，当前支持 serper */
        private String provider = "serper";

        private String serperBaseUrl = "https://google.serper.dev";

        private String serperApiKey;

        /** 期望返回的搜索结果条数 */
        private int maxResults = 5;

        /** 最多抓取正文的结果条数 */
        private int fetchLimit = 5;

        /** 单条正文最大字符数 */
        private int maxContentChars = 20000;

        /** 单页最大抓取字节数 */
        private int maxFetchBytes = 512 * 1024;

        private boolean fetchContent = true;

        /** Jina Rerank 配置，未启用时使用恒等重排 */
        private JinaConfig jina = new JinaConfig();

        @Data
        public static class JinaConfig {
            private boolean enabled = false;
            private String baseUrl = "https://api.jina.ai/v1";
            private String apiKey;
            private String model = "jina-reranker-v2-base-multilingual";
        }
    }
}
