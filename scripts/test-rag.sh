#!/bin/bash

# RAG 知识库测试脚本

BASE_URL="http://localhost:8082/api/v1/ai"
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "═══════════════════════════════════════════════════════════"
echo "   RAG 知识库测试"
echo "═══════════════════════════════════════════════════════════"
echo ""

# 1. 添加文档
echo -e "${BLUE}[1/3] 添加测试文档到知识库...${NC}"
curl -s -X POST "$BASE_URL/rag/documents" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "doc1",
      "title": "EasyWing 产品介绍",
      "content": "EasyWing Platform 是一个企业级智能体系统，支持多种 AI 能力。它包含 ReAct Agent、工具调用、技能系统、RAG 知识库等核心功能。系统采用响应式架构，支持高并发场景。",
      "source": "product.md",
      "tags": ["产品", "介绍"]
    },
    {
      "id": "doc2",
      "title": "技术架构",
      "content": "系统采用 Spring Boot 3.0 + Project Reactor 响应式架构。支持 WebSocket 实时通信、SSE 流式输出。集成 Redis 作为缓存和会话存储，支持向量数据库进行 RAG 检索。",
      "source": "architecture.md",
      "tags": ["技术", "架构"]
    },
    {
      "id": "doc3",
      "title": "核心功能",
      "content": "核心功能包括：1. ReAct Agent 推理模式 2. 6个内置工具（计算器、时间、字符串等） 3. 技能编排系统 4. 多 Agent 协作 5. RAG 知识增强 6. 可视化调试工具",
      "source": "features.md",
      "tags": ["功能"]
    }
  ]' | jq '.'

echo -e "${GREEN}✅ 文档添加成功${NC}"
echo ""

# 2. RAG 查询
echo -e "${BLUE}[2/3] RAG 查询测试...${NC}"

echo "查询 1: 产品支持哪些功能？"
curl -s -X POST "$BASE_URL/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "EasyWing 支持哪些核心功能？",
    "topK": 2,
    "model": "gpt-4o"
  }' | jq '{
    answer: .answer,
    sources: .sources | map({title, score}),
    retrievedCount: .retrievedCount
  }'

echo ""
echo "查询 2: 技术架构是什么？"
curl -s -X POST "$BASE_URL/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "系统使用了什么技术架构？",
    "topK": 2,
    "model": "gpt-4o"
  }' | jq '{
    answer: .answer,
    sources: .sources | map({title, score})
  }'

echo ""
echo -e "${GREEN}✅ RAG 查询完成${NC}"
echo ""

# 3. 删除文档
echo -e "${BLUE}[3/3] 清理测试数据...${NC}"
curl -s -X DELETE "$BASE_URL/rag/documents/doc1"
echo -e "${GREEN}✅ 测试完成${NC}"
echo ""
echo "═══════════════════════════════════════════════════════════"
