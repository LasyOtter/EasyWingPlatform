# EasyWing Platform 智能体功能开发文档

## 📋 项目概述

本次更新为 EasyWing Platform 添加了完整的智能体（Agent）功能，实现了 ReAct（Reasoning + Acting）模式，支持工具调用、技能编排、记忆管理等核心能力。

## 🎯 已实现的核心功能

### 1. **ReAct Agent 模式**

#### 实现文件
- `ReactAgent.java` - ReAct 模式核心实现
- `AgentExecutor.java` - Agent 执行器（支持简单对话和 ReAct 模式）
- `AgentIteration.java` - 迭代记录

#### 特性
- ✅ 思考-行动-观察循环（Thought-Action-Observation Loop）
- ✅ 多轮工具调用
- ✅ 自动判断何时使用 ReAct 模式
- ✅ 最大迭代次数和工具调用次数限制
- ✅ 完整的迭代历史记录

#### 使用示例
```json
POST /api/v1/ai/agent/execute
{
  "agentId": "default",
  "prompt": "计算 (25 * 8) + 12 的结果，并告诉我当前时间",
  "config": {
    "model": "gpt-4o",
    "temperature": 0.7,
    "maxIterations": 10,
    "maxToolCalls": 20,
    "timeoutMs": 60000
  }
}
```

### 2. **内置工具系统（5个工具）**

#### 已实现的工具

1. **CurrentTimeTool** - 当前时间工具
   - 支持多时区
   - 自定义时间格式
   - 返回详细时间信息

2. **CalculatorTool** - 计算器工具
   - 支持基本数学运算
   - 支持复杂表达式
   - 安全沙箱执行

3. **StringManipulationTool** - 字符串操作工具
   - 大小写转换
   - 字符串替换、分割
   - 相似度计算

4. **JsonProcessorTool** - JSON 处理工具
   - JSON 解析和验证
   - JSONPath 查询
   - JSON 合并和格式化

5. **HttpRequestTool** - HTTP 请求工具
   - 支持 GET/POST/PUT/DELETE
   - 自定义请求头
   - 响应大小限制

6. **DataAnalysisTool** - 数据分析工具
   - 统计分析（均值、中位数、标准差）
   - 数据排序和过滤
   - 直方图分组

#### 工具架构
- `AiTool` - 工具接口
- `ToolDefinition` - 工具定义（含 JSON Schema）
- `ToolExecutor` - 工具执行器（支持熔断、超时）
- `ToolRegistry` - 工具注册表
- `ToolAutoConfiguration` - 工具自动配置

### 3. **技能系统**

#### 核心组件
- `AgentSkill` - 技能定义
- `SkillExecutor` - 技能执行器接口
- `SkillResult` - 技能执行结果
- `SkillContext` - 技能执行上下文
- `SkillExecutionService` - 技能执行服务
- `SkillRegistry` - 技能注册表
- `SkillAutoConfiguration` - 技能自动配置

#### 已实现的技能

1. **InformationRetrievalSkill** - 信息检索技能
   - 并行 HTTP 请求
   - 结果聚合
   - 错误处理

2. **DataAnalysisSkill** - 数据分析技能
   - 统计分析包装
   - 多种分析类型

3. **TextProcessingSkill** - 文本处理技能
   - 文本转换
   - 字符串操作封装

#### 技能特性
- ✅ 依赖管理和拓扑排序
- ✅ 技能链（顺序执行）
- ✅ 技能组合（并行执行）
- ✅ 技能推荐
- ✅ 参数验证
- ✅ JSON Schema 支持

### 4. **记忆管理系统**

#### 实现文件
- `RedisMemoryManager.java` - Redis 记忆管理器（已完善）
- `MemoryManager.java` - 记忆管理接口
- `ConversationContext.java` - 对话上下文

#### 特性
- ✅ 对话历史存储（Redis）
- ✅ 自动过期（TTL）
- ✅ 消息追加
- ✅ 上下文检索
- ✅ JSON 序列化/反序列化

### 5. **增强功能**

#### 工具执行器增强
- ✅ Resilience4j 熔断器集成
- ✅ 超时控制
- ✅ 错误处理和重试
- ✅ 执行时间统计

#### API 增强
- ✅ 技能执行 API
- ✅ 技能链执行 API
- ✅ 技能推荐 API
- ✅ 工具执行 API

## 🔧 配置说明

### application.yml 配置示例

```yaml
easywing:
  ai:
    enabled: true
    
    # 提供商配置
    providers:
      openai:
        enabled: true
        type: openai
        apiKey: ${OPENAI_API_KEY}
        baseUrl: https://api.openai.com/v1
        model: gpt-4o
        temperature: 0.7
        maxTokens: 4096
        timeoutSeconds: 120
      
      anthropic:
        enabled: false
        type: anthropic
        apiKey: ${ANTHROPIC_API_KEY}
        model: claude-3-5-sonnet-20241022
    
    # 记忆配置
    memory:
      enabled: true
      type: redis
      maxMessages: 100
      ttlHours: 24
      summaryThreshold: 20
    
    # Agent 配置
    agent:
      maxIterations: 10
      maxToolCallsPerIteration: 5
      toolCallTimeoutMs: 30000
      streamingEnabled: false
    
    # 技能配置
    skill:
      enabled: true
      scanPackage: com.easywing.platform.ai.skill

# Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      database: 0
```

## 📡 API 接口文档

### Agent 执行

**POST** `/api/v1/ai/agent/execute`

```json
{
  "agentId": "default",
  "userId": "user123",
  "sessionId": "session-uuid",
  "prompt": "用户输入",
  "messages": [],
  "config": {
    "model": "gpt-4o",
    "temperature": 0.7,
    "maxIterations": 10,
    "maxToolCalls": 20,
    "timeoutMs": 60000
  }
}
```

**响应**
```json
{
  "id": "response-uuid",
  "agentId": "default",
  "sessionId": "session-uuid",
  "message": {
    "role": "assistant",
    "content": "助手回复"
  },
  "messages": [],
  "completed": true,
  "finishReason": "completed",
  "metadata": {
    "iterations": [],
    "iterationCount": 3,
    "totalExecutionTime": 2500
  }
}
```

### 工具执行

**POST** `/api/v1/ai/tools/execute`

```json
{
  "toolName": "calculator",
  "arguments": {
    "expression": "2 + 2 * 3"
  }
}
```

### 技能执行

**POST** `/api/v1/ai/skills/{skillName}/execute`

```json
{
  "userId": "user123",
  "sessionId": "session-uuid",
  "input": "用户输入",
  "parameters": {
    "urls": ["https://example.com"]
  }
}
```

### 技能链执行

**POST** `/api/v1/ai/skills/chain`

```json
{
  "userId": "user123",
  "sessionId": "session-uuid",
  "input": "用户输入",
  "skillNames": ["information_retrieval", "data_analysis"],
  "parameters": {}
}
```

### 技能推荐

**GET** `/api/v1/ai/skills/recommend?input=查询数据&limit=5`

## 🧪 测试示例

### 1. 测试 ReAct Agent - 计算和时间查询

```bash
curl -X POST http://localhost:8082/api/v1/ai/agent/execute \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "计算 123 * 456，然后告诉我北京的当前时间",
    "config": {
      "maxIterations": 10,
      "maxToolCalls": 20
    }
  }'
```

### 2. 测试工具 - 计算器

```bash
curl -X POST http://localhost:8082/api/v1/ai/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "calculator",
    "arguments": {
      "expression": "Math.sqrt(144) + Math.pow(2, 3)"
    }
  }'
```

### 3. 测试技能 - 信息检索

```bash
curl -X POST http://localhost:8082/api/v1/ai/skills/information_retrieval/execute \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "input": "获取网页内容",
    "parameters": {
      "urls": ["https://httpbin.org/json"]
    }
  }'
```

### 4. 测试技能链

```bash
curl -X POST http://localhost:8082/api/v1/ai/skills/chain \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "input": "文本处理",
    "skillNames": ["text_processing"],
    "parameters": {
      "text": "Hello World",
      "operation": "uppercase"
    }
  }'
```

## 📊 架构图

```
┌─────────────────────────────────────────────────────────┐
│                   AiGatewayController                    │
│                   (REST API 层)                          │
└────────────────────┬────────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          │                     │
┌─────────▼─────────┐  ┌────────▼────────┐
│   AgentExecutor   │  │ SkillExecution  │
│                   │  │    Service      │
└─────────┬─────────┘  └────────┬────────┘
          │                     │
   ┌──────┴──────┐       ┌──────┴──────┐
   │             │       │             │
┌──▼───┐  ┌─────▼─────┐ │  ┌─────────▼──────┐
│Simple│  │ReactAgent │ │  │SkillExecutor(s)│
│Chat  │  │           │ │  └────────┬────────┘
└──────┘  └─────┬─────┘ │           │
                │       │    ┌──────▼──────┐
         ┌──────┴───────┴────▼ ToolExecutor│
         │                    └──────┬──────┘
         │                           │
    ┌────▼────┐                ┌────▼─────┐
    │Provider │                │ AiTool(s)│
    │Registry │                └──────────┘
    └─────────┘
```

## 🚀 下一步计划

### 短期（1-2周）
- [ ] 添加流式响应支持（SSE）
- [ ] 实现工具 JSON Schema 验证
- [ ] 添加更多内置工具（文件操作、数据库查询）
- [ ] 完善单元测试和集成测试

### 中期（3-4周）
- [ ] 集成向量数据库（Milvus）
- [ ] 实现 RAG（检索增强生成）
- [ ] 记忆摘要和压缩
- [ ] 长期记忆支持

### 长期（1-2月）
- [ ] 多 Agent 协作
- [ ] Agent 规划能力
- [ ] 自定义技能市场
- [ ] 可视化 Agent 调试工具

## 📝 开发注意事项

### 添加新工具
1. 实现 `AiTool` 接口
2. 添加 `@Component` 注解
3. 工具会自动注册

```java
@Slf4j
@Component
public class MyCustomTool implements AiTool {
    @Override
    public String getName() {
        return "my_tool";
    }
    
    @Override
    public String getDescription() {
        return "工具描述";
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        // 实现逻辑
    }
}
```

### 添加新技能
1. 实现 `SkillExecutor` 接口
2. 在 `SkillAutoConfiguration` 中注册

```java
@Slf4j
@Component
public class MyCustomSkill implements SkillExecutor {
    @Override
    public Mono<SkillResult> execute(SkillContext context) {
        // 实现逻辑
    }
}
```

## 🐛 已知问题

1. **ReAct 提示词解析** - 当前使用正则表达式，可能需要更健壮的解析器
2. **工具并发** - 目前工具串行执行，可以优化为并行
3. **错误恢复** - 工具失败后的重试机制需要增强

## 📚 参考资料

- [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)

## 👥 贡献者

- 开发者：EasyWing Team
- 时间：2026-11-14

---

**版本**: v1.0.0  
**状态**: ✅ 核心功能已完成  
**最后更新**: 2026-11-14
