# EasyWing AI Starter

EasyWing AI Starter 是一个基于 LangChain4j 的 Spring Boot 自动配置启动器，提供大语言模型（LLM）集成、RAG（检索增强生成）、Agent 工具调用等功能。

## 特性

- ✅ **多模型支持**：OpenAI、DeepSeek、通义千问（Qwen）、Ollama（本地部署）
- ✅ **自动配置**：根据配置自动创建 ChatModel、EmbeddingModel、EmbeddingStore 等 Bean
- ✅ **聊天记忆**：支持多用户会话管理和对话历史保存
- ✅ **RAG 支持**：文档加载、分块、向量化存储与检索
- ✅ **工具调用**：支持使用 `@Tool` 注解定义和注册 AI 工具
- ✅ **Agent 框架**：支持智能体配置和工具绑定

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-ai</artifactId>
    <version>${easywing.version}</version>
</dependency>
```

> **注意**：LangChain4j 依赖已包含在 starter 中，无需手动添加。

### 2. 配置模型

#### 2.1 OpenAI 配置

```yaml
easywing:
  ai:
    chat-model:
      enabled: true
      openai:
        api-key: ${OPENAI_API_KEY:your-api-key-here}
        model-name: gpt-4o-mini
        base-url: https://api.openai.com/v1
        temperature: 0.7
        max-tokens: 4000
        log-requests: false
        log-responses: false
```

#### 2.2 DeepSeek 配置

```yaml
easywing:
  ai:
    chat-model:
      enabled: true
      deepseek:
        api-key: ${DEEPSEEK_API_KEY:your-api-key-here}
        model-name: deepseek-chat
        base-url: https://api.deepseek.com/v1
        temperature: 0.7
        max-tokens: 4000
```

#### 2.3 通义千问（Qwen）配置

```yaml
easywing:
  ai:
    chat-model:
      enabled: true
      qwen:
        api-key: ${QWEN_API_KEY:your-api-key-here}
        model-name: qwen-plus
        temperature: 0.7
        max-tokens: 4000
        enable-search: false
```

#### 2.4 Ollama 本地模型配置

```yaml
easywing:
  ai:
    chat-model:
      enabled: true
      ollama:
        base-url: http://localhost:11434
        model-name: llama3.2
        temperature: 0.7
        num-predict: 2048
```

### 3. 使用示例

#### 3.1 基础聊天对话

```java
@Service
public class AiService {

    @Autowired
    private ChatAiClient chatClient;

    public String chat(String userId, String message) {
        return chatClient.chat(userId, message);
    }
}
```

#### 3.2 带系统提示词的对话

```java
@Service
public class AiService {

    @Autowired
    private ChatAiClient chatClient;

    public String chatWithSystem(String userId, String systemPrompt, String message) {
        return chatClient.chat(userId, systemPrompt, message);
    }
}
```

#### 3.3 管理会话记忆

```java
@Service
public class ChatService {

    @Autowired
    private ChatAiClient chatClient;

    // 清除特定用户的记忆
    public void clearUserMemory(String userId) {
        chatClient.clearMemory(userId);
    }

    // 清除所有记忆
    public void clearAllMemory() {
        chatClient.clearAllMemory();
    }
}
```

## 高级功能

### RAG（检索增强生成）

#### 配置 RAG

```yaml
easywing:
  ai:
    rag:
      enabled: true
      document-loader:
        enabled: true
        resource-paths: 
          - classpath:/documents/
        chunk-size: 1000
        chunk-overlap: 200
      vector-store:
        type: in-memory
        persist-directory: ./data/vector-store
```

#### 使用 RAG 服务

```java
@Service
public class RagExample {

    @Autowired
    private RagService ragService;

    // 加载文档
    public void loadDocuments() {
        Document doc1 = ragService.createDocument("这是文档内容...");
        Document doc2 = ragService.createDocument("另一个文档...", 
            Map.of("source", "file.pdf"));
        
        ragService.ingestDocuments(List.of(doc1, doc2));
    }

    // 搜索相关内容
    public List<TextSegment> search(String query) {
        return ragService.search(query, 5); // 返回最相关的 5 个片段
    }

    // 带上下文的问答
    public String answerWithRag(String question) {
        return ragService.queryWithContext(question, 5);
    }
}
```

### Agent 工具调用

#### 1. 定义工具类

```java
@Component
public class WeatherTool {

    @Tool("获取指定城市的实时天气")
    public String getWeather(@P("城市名称") String city) {
        // 实际调用天气 API
        return "晴天，25℃";
    }

    @Tool("计算两个整数的和")
    public int add(@P("第一个数") int a, @P("第二个数") int b) {
        return a + b;
    }
}
```

#### 2. 配置工具扫描

```yaml
easywing:
  ai:
    agent:
      enabled: true
      tools:
        enabled: true
        include-packages:
          - com.example.tools
          - com.example.services
```

#### 3. 使用工具注册器

```java
@Service
public class ToolExample {

    @Autowired
    private ToolRegistry toolRegistry;

    @PostConstruct
    public void init() {
        // 获取所有已注册的工具
        List<Object> tools = toolRegistry.getTools();
        
        // 获取特定工具
        Object weatherTool = toolRegistry.getTool("weather");
        
        // 检查是否有工具
        boolean hasTools = toolRegistry.hasTools();
    }
}
```

## 配置说明

### 核心配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.enabled` | 是否启用 AI 功能 | `true` |
| `easywing.ai.chat-model.enabled` | 是否启用聊天模型 | `true` |
| `easywing.ai.rag.enabled` | 是否启用 RAG 功能 | `true` |
| `easywing.ai.agent.enabled` | 是否启用 Agent 功能 | `false` |

### ChatModel 配置

#### OpenAI

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.chat-model.openai.api-key` | OpenAI API Key | - |
| `easywing.ai.chat-model.openai.model-name` | 模型名称 | `gpt-4o-mini` |
| `easywing.ai.chat-model.openai.base-url` | API 基础 URL | `https://api.openai.com/v1` |
| `easywing.ai.chat-model.openai.temperature` | 温度（创造性） | `0.7` |
| `easywing.ai.chat-model.openai.max-tokens` | 最大 Token 数 | `4000` |
| `easywing.ai.chat-model.openai.top-p` | Top-P 采样 | `1.0` |
| `easywing.ai.chat-model.openai.timeout` | 超时时间 | `60s` |
| `easywing.ai.chat-model.openai.log-requests` | 记录请求日志 | `false` |
| `easywing.ai.chat-model.openai.log-responses` | 记录响应日志 | `false` |

#### DeepSeek

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.chat-model.deepseek.api-key` | DeepSeek API Key | - |
| `easywing.ai.chat-model.deepseek.model-name` | 模型名称 | `deepseek-chat` |
| `easywing.ai.chat-model.deepseek.base-url` | API 基础 URL | `https://api.deepseek.com/v1` |
| `easywing.ai.chat-model.deepseek.temperature` | 温度 | `0.7` |
| `easywing.ai.chat-model.deepseek.max-tokens` | 最大 Token 数 | `4000` |
| `easywing.ai.chat-model.deepseek.timeout` | 超时时间 | `60s` |

#### Qwen（通义千问）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.chat-model.qwen.api-key` | Qwen API Key | - |
| `easywing.ai.chat-model.qwen.model-name` | 模型名称 | `qwen-plus` |
| `easywing.ai.chat-model.qwen.temperature` | 温度 | `0.7` |
| `easywing.ai.chat-model.qwen.max-tokens` | 最大 Token 数 | `4000` |
| `easywing.ai.chat-model.qwen.enable-search` | 启用联网搜索 | `false` |

#### Ollama（本地部署）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.chat-model.ollama.base-url` | Ollama 服务地址 | `http://localhost:11434` |
| `easywing.ai.chat-model.ollama.model-name` | 模型名称 | `llama3.2` |
| `easywing.ai.chat-model.ollama.temperature` | 温度 | `0.7` |
| `easywing.ai.chat-model.ollama.num-predict` | 最大预测 Token 数 | `2048` |

### EmbeddingModel 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.embedding-model.provider` | 嵌入模型提供商 | `openai` |
| `easywing.ai.embedding-model.openai.api-key` | OpenAI API Key | - |
| `easywing.ai.embedding-model.openai.model-name` | 模型名称 | `text-embedding-3-small` |
| `easywing.ai.embedding-model.openai.dimensions` | 向量维度 | `1536` |
| `easywing.ai.embedding-model.local.enabled` | 启用本地嵌入模型 | `false` |
| `easywing.ai.embedding-model.local.model-name` | 本地模型名称 | `all-MiniLM-L6-v2` |

### RAG 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.rag.document-loader.resource-paths` | 文档资源路径 | `classpath:/documents/` |
| `easywing.ai.rag.document-loader.chunk-size` | 分块大小 | `1000` |
| `easywing.ai.rag.document-loader.chunk-overlap` | 分块重叠 | `200` |
| `easywing.ai.rag.vector-store.type` | 向量存储类型 | `in-memory` |
| `easywing.ai.rag.vector-store.persist-directory` | 持久化目录 | - |

### Agent 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `easywing.ai.agent.tools.enabled` | 启用工具调用 | `true` |
| `easywing.ai.agent.tools.include-packages` | 工具扫描包路径 | `["com.easywing.platform.ai.tools"]` |
| `easywing.ai.agent.memory.type` | 记忆类型 | `in-memory` |
| `easywing.ai.agent.memory.max-messages` | 最大消息数 | `100` |

## 依赖版本

本项目使用的依赖版本：

- **LangChain4j**: 1.12.1
- **LangChain4j-Dashscope**: 0.36.2
- **Spring Boot**: 自动适配

## 注意事项

1. **API Key 安全**：建议使用环境变量或配置中心管理 API Key，不要硬编码在配置文件中
2. **模型选择**：不同模型有不同的特点和适用场景，请根据实际需求选择
3. **内存管理**：使用 In-Memory Embedding Store 时注意内存使用量
4. **并发控制**：ChatAiClient 内部使用 ConcurrentHashMap 管理会话，线程安全
5. **错误处理**：建议在使用时添加适当的异常处理和重试机制

## 常见问题

### Q: 如何切换不同的模型提供商？
A: 只需在配置文件中修改对应提供商的配置即可，多个提供商可以同时配置。

### Q: 如何自定义 ChatModel？
A: 可以在自己的配置类中定义 `@Bean`，会自动覆盖自动配置的 Bean。

### Q: RAG 功能必须吗？
A: 不是必须的。可以通过 `easywing.ai.rag.enabled=false` 禁用。

### Q: 如何扩展自定义工具？
A: 创建工具类并使用 `@Tool` 注解标记方法，确保在扫描的包路径下即可。

## License

Apache License 2.0
