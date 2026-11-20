# 智能体功能快速开始指南

## 🚀 5分钟快速启动

### 1. 启动基础设施

```bash
# 启动 Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 或使用项目提供的 docker-compose
cd easywing-platform-samples
docker-compose up -d redis
```

### 2. 配置环境变量

```bash
# Linux/Mac
export OPENAI_API_KEY=your_openai_api_key

# Windows
set OPENAI_API_KEY=your_openai_api_key
```

### 3. 启动 AI 服务

```bash
cd easywing-platform-ai
mvn spring-boot:run
```

服务将在 `http://localhost:8082` 启动

### 4. 测试基本功能

#### 测试工具 - 计算器
```bash
curl -X POST http://localhost:8082/api/v1/ai/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "calculator",
    "arguments": {
      "expression": "2 + 2"
    }
  }'
```

**预期输出**:
```json
{
  "success": true,
  "expression": "2 + 2",
  "result": 4,
  "resultType": "Integer",
  "executionTime": 15,
  "toolName": "calculator"
}
```

#### 测试工具 - 当前时间
```bash
curl -X POST http://localhost:8082/api/v1/ai/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "current_time",
    "arguments": {
      "timezone": "Asia/Shanghai"
    }
  }'
```

#### 测试 ReAct Agent
```bash
curl -X POST http://localhost:8082/api/v1/ai/agent/execute \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "计算 25 乘以 8，然后告诉我现在是几点",
    "config": {
      "model": "gpt-4o",
      "maxIterations": 10,
      "maxToolCalls": 20
    }
  }'
```

**预期流程**:
```
迭代 1:
  思考: 我需要先计算 25 * 8
  行动: calculator
  行动输入: {"expression": "25 * 8"}
  观察: {"result": 200}

迭代 2:
  思考: 现在需要获取当前时间
  行动: current_time
  行动输入: {"timezone": "Asia/Shanghai"}
  观察: {"formatted": "2026-11-14 15:30:00"}

迭代 3:
  思考: 我现在知道最终答案了
  最终答案: 25 乘以 8 等于 200。现在是 2026年11月14日 15:30:00。
```

### 5. 查看可用工具和技能

```bash
# 查看所有工具
curl http://localhost:8082/api/v1/ai/tools

# 查看所有技能
curl http://localhost:8082/api/v1/ai/skills

# 查看 Agent 配置
curl http://localhost:8082/api/v1/ai/agent/config
```

## 📖 常见使用场景

### 场景 1: 数据分析

```bash
curl -X POST http://localhost:8082/api/v1/ai/agent/execute \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "对这组数据 [12, 45, 23, 67, 34, 89, 21] 进行统计分析",
    "config": {
      "maxIterations": 5,
      "maxToolCalls": 10
    }
  }'
```

### 场景 2: 文本处理

```bash
curl -X POST http://localhost:8082/api/v1/ai/skills/text_processing/execute \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "input": "转换文本",
    "parameters": {
      "text": "hello world",
      "operation": "uppercase"
    }
  }'
```

### 场景 3: 信息检索

```bash
curl -X POST http://localhost:8082/api/v1/ai/skills/information_retrieval/execute \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "input": "获取API数据",
    "parameters": {
      "urls": [
        "https://httpbin.org/json",
        "https://httpbin.org/uuid"
      ]
    }
  }'
```

### 场景 4: JSON 处理

```bash
curl -X POST http://localhost:8082/api/v1/ai/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "json_processor",
    "arguments": {
      "operation": "format",
      "json": "{\"name\":\"test\",\"value\":123}"
    }
  }'
```

## 🔍 调试技巧

### 1. 查看日志

```bash
# 查看 Agent 执行日志
tail -f logs/easywing-ai.log | grep "Agent\|ReAct\|Tool"
```

### 2. 开启详细日志

在 `application.yml` 中添加：

```yaml
logging:
  level:
    com.easywing.platform.ai.agent: DEBUG
    com.easywing.platform.ai.tool: DEBUG
    com.easywing.platform.ai.skill: DEBUG
```

### 3. 检查 Redis 连接

```bash
redis-cli
> KEYS ai:memory:*
> GET ai:memory:session-uuid
```

## ⚠️ 常见问题

### Q1: Agent 不使用工具？
**A**: 确保 `config.maxToolCalls > 0`，或者问题描述包含需要工具的关键词（计算、查询、时间等）

### Q2: Redis 连接失败？
**A**: 检查 Redis 是否运行：
```bash
redis-cli ping
# 应返回 PONG
```

### Q3: OpenAI API 调用失败？
**A**: 
- 检查 API Key 是否正确
- 检查网络连接
- 查看是否达到速率限制

### Q4: 工具执行超时？
**A**: 调整超时配置：
```yaml
easywing:
  ai:
    agent:
      toolCallTimeoutMs: 60000  # 增加到 60 秒
```

## 📊 性能指标

以下为经验参考值，仓库内未建立基准测试，非实测数据：

| 操作 | 平均响应时间 | 说明 |
|------|-------------|------|
| 简单工具调用 | < 100ms | 如计算器、时间查询 |
| HTTP 请求工具 | 1-3s | 取决于目标服务器 |
| ReAct Agent (2轮) | 3-5s | 包含 LLM 调用 |
| ReAct Agent (5轮) | 10-15s | 复杂任务 |
| 技能链执行 | 2-8s | 取决于技能数量 |

## 🎯 下一步

- 查看完整文档: [AGENT_DEVELOPMENT.md](./AGENT_DEVELOPMENT.md)
- 待改进项清单: [improvement-tasks.md](./improvement-tasks.md)

## 💡 提示

- Agent 第一次调用会较慢（LLM 模型加载）
- 建议在生产环境使用连接池和缓存
- 定期清理过期的 Redis 记忆数据
- 监控 LLM API 使用量和成本

---

**开始时间**: 2026-11-14  
**预计完成**: 5-10分钟  
**难度**: ⭐⭐☆☆☆
