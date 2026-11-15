#!/bin/bash

# 多 Agent 协作测试脚本

BASE_URL="http://localhost:8082/api/v1/ai"
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "═══════════════════════════════════════════════════════════"
echo "   多 Agent 协作测试"
echo "═══════════════════════════════════════════════════════════"
echo ""

# 1. 顺序协作
echo -e "${BLUE}[1/4] 测试顺序协作 (Sequential)...${NC}"
echo "任务: 设计一个用户注册功能"
echo ""

SEQUENTIAL_RESULT=$(curl -s -X POST "$BASE_URL/multiagent/sequential" \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["planner", "executor", "reviewer"],
    "task": "设计一个用户注册功能，包括邮箱验证",
    "model": "gpt-4o",
    "maxToolCalls": 5
  }')

echo $SEQUENTIAL_RESULT | jq '{
  collaborationType: .collaborationType,
  agentCount: (.agentResponses | length),
  executionTime: .totalExecutionTimeMs,
  finalResult: .finalResult
}'

echo -e "${GREEN}✅ 顺序协作完成${NC}"
echo ""

# 2. 并行协作
echo -e "${BLUE}[2/4] 测试并行协作 (Parallel)...${NC}"
echo "任务: 分析三个方面"
echo ""

PARALLEL_RESULT=$(curl -s -X POST "$BASE_URL/multiagent/parallel" \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["researcher", "researcher", "researcher"],
    "task": "分别从技术、业务、成本三个角度分析云原生架构",
    "model": "gpt-4o"
  }')

echo $PARALLEL_RESULT | jq '{
  collaborationType: .collaborationType,
  agentCount: (.agentResponses | length),
  executionTime: .totalExecutionTimeMs
}'

echo -e "${GREEN}✅ 并行协作完成${NC}"
echo ""

# 3. 辩论模式
echo -e "${BLUE}[3/4] 测试辩论协作 (Debate)...${NC}"
echo "话题: 是否应该使用微服务架构？"
echo ""

DEBATE_RESULT=$(curl -s -X POST "$BASE_URL/multiagent/debate" \
  -H "Content-Type: application/json" \
  -d '{
    "agentIds": ["agent1", "agent2", "agent3"],
    "task": "讨论：小型团队是否应该使用微服务架构？",
    "maxRounds": 2,
    "model": "gpt-4o"
  }')

echo $DEBATE_RESULT | jq '{
  collaborationType: .collaborationType,
  rounds: (.debateRounds | length),
  executionTime: .totalExecutionTimeMs,
  conclusion: .finalResult
}'

echo -e "${GREEN}✅ 辩论协作完成${NC}"
echo ""

# 4. 层级协作
echo -e "${BLUE}[4/4] 测试层级协作 (Hierarchical)...${NC}"
echo "任务: 开发聊天机器人"
echo ""

HIERARCHICAL_RESULT=$(curl -s -X POST "$BASE_URL/multiagent/hierarchical" \
  -H "Content-Type: application/json" \
  -d '{
    "task": "开发一个智能客服聊天机器人",
    "model": "gpt-4o"
  }')

echo $HIERARCHICAL_RESULT | jq '{
  collaborationType: .collaborationType,
  phases: (.agentResponses | map(.agentId)),
  executionTime: .totalExecutionTimeMs,
  finalResult: .finalResult
}'

echo -e "${GREEN}✅ 层级协作完成${NC}"
echo ""

# 总结
echo "═══════════════════════════════════════════════════════════"
echo "   测试总结"
echo "═══════════════════════════════════════════════════════════"
echo -e "${GREEN}✅ 所有协作模式测试完成${NC}"
echo ""
echo "协作模式:"
echo "  ✅ Sequential  - 顺序执行 (规划→执行→评审)"
echo "  ✅ Parallel    - 并行执行 (多角度分析)"
echo "  ✅ Debate      - 辩论讨论 (多轮观点交换)"
echo "  ✅ Hierarchical - 层级执行 (自动编排)"
echo ""
echo "═══════════════════════════════════════════════════════════"
