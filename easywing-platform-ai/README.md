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

## License

MIT