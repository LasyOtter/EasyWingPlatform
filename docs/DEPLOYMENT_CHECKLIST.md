# 智能体功能部署清单

## ✅ 部署前检查

### 1. 环境准备
- [ ] Java 21+ 已安装
- [ ] Maven 3.9.0+ 已安装
- [ ] Redis 已启动（端口 6379）
- [ ] Docker 已安装（可选，用于 Redis）

### 2. 配置检查
- [ ] 已设置 `OPENAI_API_KEY` 环境变量
- [ ] 已复制 `application-example.yml` 到 `application.yml`
- [ ] Redis 连接信息已配置
- [ ] 日志目录有写权限

### 3. 依赖检查
```bash
cd easywing-platform-ai
mvn dependency:tree | grep -E "langchain4j|redis|resilience4j"
```

预期输出应包含：
- ✅ langchain4j
- ✅ spring-boot-starter-data-redis
- ✅ resilience4j-spring-boot3

## 📦 部署步骤

### 步骤 1: 编译项目
```bash
cd easywing-platform-ai
mvn clean install -DskipTests
```

**预期结果**: `BUILD SUCCESS`

### 步骤 2: 启动 Redis
```bash
# 使用 Docker
docker run -d --name redis \
  -p 6379:6379 \
  redis:7-alpine

# 验证 Redis
redis-cli ping
# 应返回: PONG
```

### 步骤 3: 设置环境变量
```bash
# Linux/Mac
export OPENAI_API_KEY=sk-xxx

# Windows PowerShell
$env:OPENAI_API_KEY="sk-xxx"

# Windows CMD
set OPENAI_API_KEY=sk-xxx
```

### 步骤 4: 启动服务
```bash
cd easywing-platform-ai
mvn spring-boot:run
```

**预期日志输出**:
```
Initializing tools, found 6 tool beans
Tool initialization completed, 6 tools available
Registered skill: information_retrieval
Registered skill: data_analysis
Registered skill: text_processing
Skill initialization completed, 3 skills registered
Started EasyWingAiApplication in 8.5 seconds
```

### 步骤 5: 健康检查
```bash
# 检查服务健康
curl http://localhost:8082/api/v1/ai/health

# 预期输出
{"status":"UP"}
```

### 步骤 6: 功能验证
```bash
# 1. 验证工具列表
curl http://localhost:8082/api/v1/ai/tools | jq

# 预期: 返回 6 个工具

# 2. 验证技能列表
curl http://localhost:8082/api/v1/ai/skills | jq

# 预期: 返回 3 个技能

# 3. 测试简单工具
curl -X POST http://localhost:8082/api/v1/ai/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"toolName":"calculator","arguments":{"expression":"2+2"}}'

# 预期: {"success":true,"result":4}

# 4. 测试 ReAct Agent
curl -X POST http://localhost:8082/api/v1/ai/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"prompt":"计算1+1","config":{"maxToolCalls":10}}'

# 预期: 返回包含迭代信息的完整响应
```

## 🔍 故障排查

### 问题 1: Redis 连接失败
**现象**: `Unable to connect to Redis`

**解决**:
```bash
# 检查 Redis 是否运行
docker ps | grep redis

# 检查端口
netstat -an | grep 6379

# 重启 Redis
docker restart redis
```

### 问题 2: OpenAI API 调用失败
**现象**: `401 Unauthorized` 或 `Invalid API Key`

**解决**:
```bash
# 验证 API Key
echo $OPENAI_API_KEY

# 测试 API Key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

### 问题 3: 工具未注册
**现象**: `Tool not found: xxx`

**解决**:
```bash
# 检查日志
tail -f logs/easywing-ai.log | grep "Tool initialization"

# 预期应该看到 6 个工具注册日志
```

### 问题 4: 端口被占用
**现象**: `Port 8082 is already in use`

**解决**:
```bash
# 查找占用进程
lsof -i :8082

# 修改端口（application.yml）
server:
  port: 8083
```

## 📊 性能监控

### 1. 查看日志
```bash
# 实时日志
tail -f logs/easywing-ai.log

# 过滤 Agent 日志
tail -f logs/easywing-ai.log | grep "Agent\|ReAct"

# 过滤工具执行日志
tail -f logs/easywing-ai.log | grep "Tool.*executed"
```

### 2. 监控 Redis
```bash
redis-cli
> INFO stats
> KEYS ai:memory:*
> GET ai:memory:session-xxx
```

### 3. JVM 监控
```bash
# 使用 jconsole 或 VisualVM
jconsole
```

## 🚀 生产环境部署

### 1. 构建 JAR
```bash
mvn clean package -DskipTests
```

### 2. 运行 JAR
```bash
java -jar target/easywing-platform-ai-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8082
```

### 3. 使用 Docker 部署
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# 构建镜像
docker build -t easywing-ai:latest .

# 运行容器
docker run -d \
  --name easywing-ai \
  -p 8082:8082 \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  -e SPRING_DATA_REDIS_HOST=redis \
  --link redis:redis \
  easywing-ai:latest
```

### 4. 使用 docker-compose
```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  easywing-ai:
    build: .
    ports:
      - "8082:8082"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - redis

volumes:
  redis-data:
```

```bash
docker-compose up -d
```

## 📈 监控指标

### 关键指标
- **服务可用性**: > 99.9%
- **平均响应时间**: < 3秒
- **工具调用成功率**: > 95%
- **Redis 内存使用**: < 1GB
- **JVM 堆内存**: < 2GB

### Prometheus 监控
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'easywing-ai'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8082']
```

### Grafana 面板
推荐监控面板：
1. JVM 内存和 GC
2. HTTP 请求统计
3. Agent 执行时间
4. 工具调用统计
5. Redis 连接池

## 🔐 安全检查

### 1. API Key 安全
- [ ] 不要在代码中硬编码 API Key
- [ ] 使用环境变量或密钥管理服务
- [ ] 定期轮换 API Key

### 2. Redis 安全
- [ ] 设置 Redis 密码
- [ ] 限制 Redis 网络访问
- [ ] 启用 Redis TLS

### 3. API 安全
- [ ] 添加认证（OAuth2/JWT）
- [ ] 添加速率限制
- [ ] 启用 CORS 保护

## ✅ 部署完成检查清单

部署成功后，应该能够：
- [x] 服务正常启动（日志无错误）
- [x] 健康检查通过
- [x] 查询到 6 个工具
- [x] 查询到 3 个技能
- [x] 计算器工具正常工作
- [x] ReAct Agent 能够调用工具
- [x] 记忆存储到 Redis
- [x] 所有 API 端点响应正常

## 📞 支持

遇到问题？
1. 查看文档: [AGENT_DEVELOPMENT.md](./AGENT_DEVELOPMENT.md)
2. 查看日志: `logs/easywing-ai.log`
3. 提交 Issue: GitHub Issues

---

**版本**: v1.0.0  
**最后更新**: 2026-11-14  
**维护者**: EasyWing Team
