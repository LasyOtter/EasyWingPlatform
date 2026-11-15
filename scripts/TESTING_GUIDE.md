# EasyWing Platform 测试指南

## 🚀 快速开始测试

### 前置准备

1. **启动 Redis**
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

2. **设置环境变量**
```bash
export OPENAI_API_KEY=your_api_key_here
```

3. **启动服务**
```bash
cd easywing-platform-ai
mvn spring-boot:run
```

---

## 📋 测试清单

### ✅ 基础功能测试

#### 1. 运行完整测试套件
```bash
cd scripts
chmod +x test-all-features.sh
./test-all-features.sh
```

**测试内容**:
- ✅ 服务健康检查
- ✅ 工具列表和执行
- ✅ 工具缓存性能
- ✅ 并行工具调用
- ✅ Agent 执行
- ✅ 技能系统
- ✅ 会话管理
- ✅ 调试追踪

---

### 🌐 WebSocket 实时通信测试

#### 1. 使用浏览器测试
```bash
# 在浏览器中打开
open scripts/websocket-test.html
```

**测试步骤**:
1. 点击"连接"按钮
2. 输入提示词，如："计算 25 * 8 并告诉我现在几点"
3. 点击"执行 Agent"
4. 观察实时事件流
5. 测试"取消执行"和"心跳"功能

**预期结果**:
- 看到实时的思考、工具调用、观察事件
- 可以随时取消执行
- 心跳保持连接活跃

#### 2. 使用 JavaScript 客户端
```javascript
// 在浏览器控制台
const ws = new WebSocket('ws://localhost:8082/ws/agent');

ws.onopen = () => {
  console.log('Connected');
  ws.send(JSON.stringify({
    type: 'EXECUTE',
    data: {
      prompt: '计算 2+2',
      model: 'gpt-4o',
      maxIterations: 10
    }
  }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log('Event:', msg.type, msg.data);
};
```

---

### 📚 RAG 知识库测试

#### 1. 运行 RAG 测试脚本
```bash
chmod +x test-rag.sh
./test-rag.sh
```

#### 2. 手动测试

**添加文档**:
```bash
curl -X POST http://localhost:8082/api/v1/ai/rag/documents \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "doc1",
      "title": "产品文档",
      "content": "这是产品的详细介绍...",
      "source": "product.md",
      "tags": ["产品"]
    }
  ]'
```

**RAG 查询**:
```bash
curl -X POST http://localhost:8082/api/v1/ai/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "产品有什么功能？",
    "topK": 3,
    "model": "gpt-4o"
  }' | jq
```

**预期结果**:
```json
{
  "answer": "根据产品文档，产品具有以下功能...",
  "sources": [
    {
      "title": "产品文档",
      "content": "这是产品的详细介绍...",
      "score": 0.95
    }
  ],
  "retrievedCount": 1
}
```

---

### 🤝 多 Agent 协作测试

#### 1. 运行多 Agent 测试脚本
```bash
chmod +x test-multiagent.sh
./test-multiagent.sh
```

#### 2. 手动测试各种协作模式

**顺序协作** (规划→执行→评审):
```bash
curl -X POST http://localhost:8082/api/v1/ai/multiagent/sequential \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["planner", "executor", "reviewer"],
    "task": "设计一个用户注册功能",
    "model": "gpt-4o"
  }' | jq
```

**并行协作** (多角度分析):
```bash
curl -X POST http://localhost:8082/api/v1/ai/multiagent/parallel \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["researcher", "researcher", "researcher"],
    "task": "分析云原生架构的技术、业务、成本",
    "model": "gpt-4o"
  }' | jq
```

**辩论模式** (多轮讨论):
```bash
curl -X POST http://localhost:8082/api/v1/ai/multiagent/debate \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["agent1", "agent2", "agent3"],
    "task": "讨论是否应该使用微服务架构",
    "maxRounds": 3,
    "model": "gpt-4o"
  }' | jq
```

**层级协作** (自动编排):
```bash
curl -X POST http://localhost:8082/api/v1/ai/multiagent/hierarchical \
  -H "Content-Type: application/json" \
  -d '{
    "task": "开发一个智能客服聊天机器人",
    "model": "gpt-4o"
  }' | jq
```

---

### 🔍 调试工具测试

#### 1. 查看执行追踪
```bash
# 获取最近的追踪
curl http://localhost:8082/api/v1/ai/debug/traces/recent?limit=10 | jq

# 获取特定追踪
curl http://localhost:8082/api/v1/ai/debug/traces/{traceId} | jq
```

#### 2. 生成调试报告
```bash
# 先执行一个 Agent
RESULT=$(curl -s -X POST http://localhost:8082/api/v1/ai/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"prompt":"计算2+2","config":{"maxToolCalls":10}}')

# 获取 traceId (需要从日志或响应中获取)
TRACE_ID="trace-xxx"

# 生成报告
curl http://localhost:8082/api/v1/ai/debug/report/$TRACE_ID | jq
```

**报告内容**:
- 执行摘要
- 性能分析 (LLM vs 工具耗时)
- 迭代分析
- 工具统计 (调用次数、成功率、耗时)
- 优化建议

---

### ⚡ 流式响应测试

#### 1. SSE 流式输出
```bash
curl -N "http://localhost:8082/api/v1/ai/agent/execute/stream?prompt=计算25*8并告诉我时间&maxIterations=10"
```

**预期输出**:
```
event: START
data: {"type":"START","timestamp":1700000000}

event: THINKING
data: {"type":"THINKING","data":"我需要先计算..."}

event: TOOL_CALL_START
data: {"type":"TOOL_CALL_START","data":{"toolName":"calculator"}}

event: TOOL_CALL_END
data: {"type":"TOOL_CALL_END","data":{"result":200}}

event: FINAL_ANSWER
data: {"type":"FINAL_ANSWER","data":"25*8=200..."}
```

---

## 🧪 性能测试

### 1. 缓存性能对比
```bash
echo "无缓存："
time curl -s -X POST "http://localhost:8082/api/v1/ai/tools/execute" \
  -d '{"toolName":"calculator","arguments":{"expression":"123*456"}}'

echo "有缓存："
time curl -s -X POST "http://localhost:8082/api/v1/ai/tools/execute?useCache=true" \
  -d '{"toolName":"calculator","arguments":{"expression":"123*456"}}'

time curl -s -X POST "http://localhost:8082/api/v1/ai/tools/execute?useCache=true" \
  -d '{"toolName":"calculator","arguments":{"expression":"123*456"}}'
```

### 2. 并行 vs 串行对比
```bash
echo "串行执行："
time curl -s -X POST http://localhost:8082/api/v1/ai/tools/execute/batch \
  -d '{"parallel":false,"toolCalls":[...]}'

echo "并行执行："
time curl -s -X POST http://localhost:8082/api/v1/ai/tools/execute/batch \
  -d '{"parallel":true,"toolCalls":[...]}'
```

---

## 📊 监控和统计

### 查看缓存统计
```bash
curl http://localhost:8082/api/v1/ai/tools/cache/stats | jq
```

### 查看所有追踪
```bash
curl http://localhost:8082/api/v1/ai/debug/traces | jq
```

### 查看会话列表
```bash
curl http://localhost:8082/api/v1/ai/sessions/{userId} | jq
```

---

## ✅ 测试检查清单

完成以下测试后，你的系统已经过充分验证：

- [ ] 服务启动成功
- [ ] 工具执行正常
- [ ] 缓存工作正常
- [ ] 并行执行提速明显
- [ ] Agent 能够调用工具
- [ ] WebSocket 实时通信正常
- [ ] RAG 检索准确
- [ ] 多 Agent 协作成功
- [ ] 调试追踪完整
- [ ] 流式响应实时

---

## 🐛 常见问题

### Q: WebSocket 连接失败？
**A**: 检查端口 8082 是否被占用，确认服务已启动

### Q: RAG 查询结果不准确？
**A**: 当前使用模拟 embedding，生产环境需集成 OpenAI Embedding API

### Q: 多 Agent 执行慢？
**A**: 正常现象，多个 LLM 调用需要时间，可以查看调试报告分析

### Q: 工具调用失败？
**A**: 检查参数格式，使用 JSON Schema 验证

---

## 📝 测试报告模板

测试完成后，记录以下信息：

```
测试日期: ____
测试人员: ____

功能测试:
- 基础功能: ✅/❌
- WebSocket: ✅/❌
- RAG: ✅/❌
- 多 Agent: ✅/❌
- 调试工具: ✅/❌

性能测试:
- 缓存命中率: __%
- 并行加速比: __x
- 平均响应时间: __ms

问题记录:
1. 
2. 

改进建议:
1. 
2. 
```

---

**测试愉快！🎉**
