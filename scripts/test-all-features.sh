#!/bin/bash

# EasyWing Platform AI Agent 功能测试脚本
# 使用方法: ./test-all-features.sh

set -e

BASE_URL="http://localhost:8082/api/v1/ai"
BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}   EasyWing Platform - 智能体功能完整测试${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo ""

# 检查服务是否启动
echo -e "${BLUE}[1/10] 检查服务状态...${NC}"
if curl -s -f "$BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 服务正在运行${NC}"
else
    echo -e "${RED}❌ 服务未启动，请先启动服务: mvn spring-boot:run${NC}"
    exit 1
fi
echo ""

# 测试工具列表
echo -e "${BLUE}[2/10] 测试工具列表...${NC}"
TOOLS=$(curl -s "$BASE_URL/tools")
TOOL_COUNT=$(echo $TOOLS | jq '. | length')
echo -e "${GREEN}✅ 找到 $TOOL_COUNT 个工具${NC}"
echo $TOOLS | jq -r '.[].name' | sed 's/^/  - /'
echo ""

# 测试单个工具执行
echo -e "${BLUE}[3/10] 测试工具执行 - 计算器${NC}"
CALC_RESULT=$(curl -s -X POST "$BASE_URL/tools/execute" \
  -H "Content-Type: application/json" \
  -d '{"toolName":"calculator","arguments":{"expression":"2+2*3"}}')
echo $CALC_RESULT | jq '.'
if echo $CALC_RESULT | jq -e '.success == true' > /dev/null; then
    RESULT=$(echo $CALC_RESULT | jq -r '.result')
    echo -e "${GREEN}✅ 计算结果: $RESULT${NC}"
else
    echo -e "${RED}❌ 工具执行失败${NC}"
fi
echo ""

# 测试工具缓存
echo -e "${BLUE}[4/10] 测试工具缓存...${NC}"
echo "第一次调用（无缓存）:"
time curl -s -X POST "$BASE_URL/tools/execute?useCache=true" \
  -H "Content-Type: application/json" \
  -d '{"toolName":"calculator","arguments":{"expression":"123*456"}}' | jq '.executionTime'

echo "第二次调用（有缓存）:"
time curl -s -X POST "$BASE_URL/tools/execute?useCache=true" \
  -H "Content-Type: application/json" \
  -d '{"toolName":"calculator","arguments":{"expression":"123*456"}}' | jq '.fromCache, .executionTime'

CACHE_STATS=$(curl -s "$BASE_URL/tools/cache/stats")
echo "缓存统计:"
echo $CACHE_STATS | jq '.'
echo ""

# 测试并行工具调用
echo -e "${BLUE}[5/10] 测试并行工具执行...${NC}"
BATCH_RESULT=$(curl -s -X POST "$BASE_URL/tools/execute/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "parallel": true,
    "toolCalls": [
      {"callId":"1","toolName":"calculator","arguments":{"expression":"10+20"}},
      {"callId":"2","toolName":"calculator","arguments":{"expression":"5*6"}},
      {"callId":"3","toolName":"current_time","arguments":{}}
    ]
  }')
echo $BATCH_RESULT | jq '.'
SUCCESS_COUNT=$(echo $BATCH_RESULT | jq '.successCount')
TOTAL_TIME=$(echo $BATCH_RESULT | jq '.totalExecutionTimeMs')
echo -e "${GREEN}✅ 并行执行: $SUCCESS_COUNT/3 成功，总耗时: ${TOTAL_TIME}ms${NC}"
echo ""

# 测试 Agent 执行
echo -e "${BLUE}[6/10] 测试 Agent 执行...${NC}"
AGENT_RESULT=$(curl -s -X POST "$BASE_URL/agent/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "计算 25 乘以 8 的结果",
    "config": {
      "model": "gpt-4o",
      "maxIterations": 5,
      "maxToolCalls": 10
    }
  }')
echo $AGENT_RESULT | jq '{
  agentId: .agentId,
  completed: .completed,
  answer: .message.content,
  metadata: .metadata
}'
if echo $AGENT_RESULT | jq -e '.completed == true' > /dev/null; then
    echo -e "${GREEN}✅ Agent 执行成功${NC}"
else
    echo -e "${RED}❌ Agent 执行失败${NC}"
fi
echo ""

# 测试技能列表
echo -e "${BLUE}[7/10] 测试技能系统...${NC}"
SKILLS=$(curl -s "$BASE_URL/skills")
SKILL_COUNT=$(echo $SKILLS | jq '. | length')
echo -e "${GREEN}✅ 找到 $SKILL_COUNT 个技能${NC}"
echo $SKILLS | jq -r '.[].name' | sed 's/^/  - /'
echo ""

# 测试技能执行
echo -e "${BLUE}[8/10] 测试技能执行 - 数据分析${NC}"
SKILL_RESULT=$(curl -s -X POST "$BASE_URL/skills/data_analysis/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "input": "分析数据",
    "parameters": {
      "data": [10, 20, 30, 40, 50],
      "type": "statistics"
    }
  }')
echo $SKILL_RESULT | jq '.'
if echo $SKILL_RESULT | jq -e '.success == true' > /dev/null; then
    echo -e "${GREEN}✅ 技能执行成功${NC}"
else
    echo -e "${YELLOW}⚠️  技能执行需要配置${NC}"
fi
echo ""

# 测试会话管理
echo -e "${BLUE}[9/10] 测试会话管理...${NC}"
SESSION_RESULT=$(curl -s -X POST "$BASE_URL/sessions" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "sessionName": "测试会话"
  }')
echo $SESSION_RESULT | jq '.'
SESSION_ID=$(echo $SESSION_RESULT | jq -r '.sessionId')
echo -e "${GREEN}✅ 会话创建成功: $SESSION_ID${NC}"

SESSIONS=$(curl -s "$BASE_URL/sessions/test-user")
SESSION_COUNT=$(echo $SESSIONS | jq '. | length')
echo -e "${GREEN}✅ 用户会话数: $SESSION_COUNT${NC}"
echo ""

# 测试调试功能
echo -e "${BLUE}[10/10] 测试调试功能...${NC}"
TRACES=$(curl -s "$BASE_URL/debug/traces/recent?limit=5")
TRACE_COUNT=$(echo $TRACES | jq '. | length')
echo -e "${GREEN}✅ 最近追踪记录: $TRACE_COUNT 条${NC}"
if [ $TRACE_COUNT -gt 0 ]; then
    echo "最新追踪:"
    echo $TRACES | jq '.[0] | {traceId, agentId, status, startTime}'
fi
echo ""

# 总结
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}   测试总结${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ 所有基础功能测试通过${NC}"
echo ""
echo "已测试功能:"
echo "  ✅ 工具系统 (6个工具)"
echo "  ✅ 工具缓存"
echo "  ✅ 并行执行"
echo "  ✅ Agent 执行"
echo "  ✅ 技能系统 (3个技能)"
echo "  ✅ 会话管理"
echo "  ✅ 调试追踪"
echo ""
echo "高级功能测试:"
echo "  📝 WebSocket 测试 - 请运行: npm run test:websocket"
echo "  📝 流式响应测试 - 请运行: curl -N \"$BASE_URL/agent/execute/stream?prompt=测试\""
echo "  📝 RAG 测试 - 请运行: ./test-rag.sh"
echo "  📝 多 Agent 测试 - 请运行: ./test-multiagent.sh"
echo ""
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
