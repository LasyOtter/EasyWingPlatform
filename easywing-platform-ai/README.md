# EasyWing Platform AI

企业级 AI 服务模块，支持多模型提供商、Agent、MCP、知识库和工具调用。

## 核心特性

### 1. 多模型提供商支持

- **OpenAI**: GPT-4o, GPT-4o Mini, GPT-4 Turbo, GPT-3.5 Turbo
- **Anthropic**: Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Sonnet
- **Google**: Gemini 2.0 Flash, Gemini 1.5 Pro, Gemini 1.5 Flash

### 2. 标准协议兼容

- **Chat Completions API**: OpenAI 兼容
- **Messages API**: Anthropic 兼容
- **Responses API**: Google Gemini 兼容
- **交错思考**: 支持 Claude 的 thinking 特性

### 3. MCP (Model Context Protocol)

- 标准化工具调用协议
- 资源管理
- 提示模板
- 跨平台兼容

### 4. Agent 框架

- 多轮对话支持
- 工具调用链
- 技能系统
- 迭代执行控制

### 5. 记忆与上下文管理

- Redis 分布式存储
- 智能上下文压缩
- 会话摘要生成
- 历史消息检索

### 6. SKILL 系统

- 技能注册与发现
- 内置代码助手技能
- 内置数据分析技能
- 可扩展技能架构

### 7. 工具调用

- 统一的工具接口
- 工具注册表
- 异步执行支持
- 执行结果缓存

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-platform-ai</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 配置 API Key

```yaml
easywing:
  ai:
    enabled: true
    providers:
      openai:
        enabled: true
        apiKey: ${OPENAI_API_KEY}
        model: gpt-4o
      anthropic:
        enabled: true
        apiKey: ${ANTHROPIC_API_KEY}
        model: claude-3-5-sonnet-20241022
```

### 3. 使用 AI 服务

```java
@Autowired
private AiProviderRegistry providerRegistry;

public void chat() {
    ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model("gpt-4o")
            .messages(List.of(
                    ChatMessage.builder()
                            .role("user")
                            .content("Hello, how are you?")
                            .build()
            ))
            .temperature(0.7)
            .build();

    ChatCompletionResponse response = providerRegistry.chat(request).block();
}
```

## API 端点

### Chat Completions

```bash
POST /api/v1/ai/chat/completions

{
    "model": "gpt-4o",
    "messages": [
        {"role": "user", "content": "Hello!"}
    ],
    "temperature": 0.7
}
```

### Streaming Chat

```bash
POST /api/v1/ai/chat/completions/stream
Content-Type: text/event-stream
```

### List Models

```bash
GET /api/v1/ai/models
```

## 工具调用

### 定义工具

```java
@Component
public class MyTool implements AiTool {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public String getDescription() {
        return "Description of what this tool does";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "param1", Map.of("type", "string", "description", "Parameter description")
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        // Tool logic
        return Mono.just(Map.of("result", "success"));
    }
}
```

### 使用工具

```java
@Autowired
private ToolExecutor toolExecutor;

public void callTool() {
    Map<String, Object> result = toolExecutor.executeTool("my_tool", Map.of("param1", "value"))
            .block();
}
```

## MCP 协议

### 连接到 MCP 服务器

```java
@Autowired
private McpClient mcpClient;

public void connectMcp() {
    McpClient.McpSession session = mcpClient.connect(
            "http://localhost:8080/mcp",
            McpRequest.McpClientInfo.builder()
                    .name("my-client")
                    .version("1.0.0")
                    .build()
    ).block();

    // List available tools
    List<McpTool> tools = session.listTools().block();
}
```

## 会话管理

```java
@Autowired
private SessionManager sessionManager;

public void manageSession() {
    // Create session
    ConversationSession session = sessionManager.createSession("user123", "My Chat")
            .block();

    // Get session
    Optional<ConversationSession> found = sessionManager.getSession(session.getId())
            .block();

    // Archive session
    sessionManager.archiveSession(session.getId()).block();
}
```

## 上下文压缩

当对话长度超过阈值时，系统自动压缩上下文：

```yaml
easywing:
  ai:
    compression:
      enabled: true
      targetTokens: 8000
      compressionRatio: 0.5
```

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      AI Gateway                             │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Chat        │  │ Messages     │  │ Responses        │  │
│  │ Completions │  │ API          │  │ API              │  │
│  └─────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Provider Registry                        │
│  ┌──────────┐  ┌───────────┐  ┌────────┐  ┌─────────────┐  │
│  │ OpenAI   │  │ Anthropic │  │ Google │  │ Custom      │  │
│  │ Provider │  │ Provider  │  │ Provider│  │ Provider    │  │
│  └──────────┘  └───────────┘  └────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Agent Executor                         │
│  ┌────────────┐  ┌──────────┐  ┌────────┐  ┌────────────┐   │
│  │ Skill      │  │ Tool     │  │ Memory │  │ Compression│   │
│  │ Registry   │  │ Executor │  │ Manager│  │ Context    │   │
│  └────────────┘  └──────────┘  └────────┘  └────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    MCP Protocol Layer                       │
│  ┌────────────┐  ┌──────────┐  ┌────────────────────────┐   │
│  │ MCP Server │  │ MCP Client│  │ Tool Definitions       │   │
│  └────────────┘  └──────────┘  └────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 异常处理

服务通过 `AiGlobalExceptionHandler` 将异常统一转换为 RFC 9457 Problem Details
（`Content-Type: application/problem+json`），扩展字段与平台其他服务保持一致：
`errorCode`、`timestamp`、`traceId`、`errors`。

覆盖 `BizException`、`SystemException`、`ValidationException`、`@Valid` 参数校验、
参数缺失 / 类型不匹配、请求体不可读、404、`AccessDeniedException` 以及兜底异常。
未认证请求由 Spring Security 的 `JwtAuthenticationEntryPoint` 直接返回
`401 application/problem+json`（`errorCode=AUTH001`），`traceId` 优先取请求头 `X-Trace-Id`。

## 安全

### 认证

服务引入 `easywing-boot-starter-security-oauth2`，作为 OAuth2 资源服务器校验 JWT。
配置前缀与 `system` 服务一致：

```yaml
easywing:
  security:
    public-paths:
      - /actuator/**
      - /api/v1/ai/health
      - /swagger-ui/**
      - /v3/api-docs/**
    oauth2:
      jwt:
        jwk-set-uri: ${JWK_SET_URI:http://auth-service/.well-known/jwks.json}
        authorities-claim-name: roles
        username-claim-name: preferred_username
```

除 `public-paths` 外的全部 `/api/v1/ai/**` 端点都需要 `Authorization: Bearer <token>`。

### WebSocket

`/ws/agent` 的握手请求**不经过** Spring Security 过滤器链，由
`WebSocketTokenInterceptor` 在握手阶段独立校验 JWT（`easywing.ai.websocket.require-token`
默认 `true`）。浏览器端无法设置自定义请求头，令牌通过查询参数传入：

```javascript
const ws = new WebSocket(`ws://localhost:8082/ws/agent?token=${accessToken}`);
```

### 直连风险

服务默认监听 `8082`。生产部署应通过网关（`/api/v1/ai/**` 路由）暴露，
避免将 `8082` 直接暴露给不可信网络。

## 未接入的组件

以下类已注册为 Spring Bean，但**没有任何调用方与 API 暴露**（全仓引用计数为 0，仅自身定义）。
保留供后续接入，接入前不应依赖其行为：

`PlanningAgent`、`SelfReflectionAgent`、`TaskDecompositionAgent`、`EnhancedFunctionCallingAgent`、
`CostOptimizationEngine`、`BatchOptimizationEngine`、`PromptOptimizer`、`ToolRecommender`、
`LongTermMemoryManager`、`ContextCompressor`、`MultiModalService`、`MonitoringDashboard`、
`ErrorRecoveryService`、`AgentTemplateLibrary`、`ABTestingFramework`、`McpClient` / `McpServer`、
`AgentCommunicationBus`（合计约 6,200 行）

注意 `FunctionCallingAgent` 不在此列：它被 `AgentExecutor` 注入并在工具模式中使用。

`CodeExecutionSandbox` 为 `@ConditionalOnProperty(easywing.ai.sandbox.enabled)`，**默认不注册**。
其实现为 `ProcessBuilder` 子进程隔离，无容器边界，字符串黑名单可被 base64 / 反射绕过，
启用前必须自行评估风险。

`EmbeddingService` 在配置 OpenAI API Key 时调用真实 `/v1/embeddings`；未配置时退化为
**确定性随机向量**（无语义，RAG 检索结果不可用，启动时打印 WARN）。
`MultiModalService` 的图像/音频/文档处理器仍返回模拟数据。

## License

MIT
