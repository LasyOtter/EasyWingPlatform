# EasyWing Platform Gateway

高性能企业级API网关，集成JWT校验、限流、灰度发布、日志脱敏等核心功能。

## 核心功能

### 1. JWT校验模块
- JWT Token解析与验证（支持RS256/ES256）
- JWK Set动态刷新（后台线程，不阻塞请求）
- Token黑名单（Redis存储）
- 多Issuer支持（企业多租户场景）
- 免鉴权路径白名单配置
- Caffeine本地缓存（缓存命中率>80%）

### 2. 限流模块
- 分布式限流（Redis + Lua脚本，保证原子性）
- 多级限流策略：全局、API、用户、IP
- 令牌桶/漏桶/滑动窗口算法支持
- 本地令牌桶预热（减少Redis访问）
- Redis故障自动降级

### 3. 灰度发布模块
- 流量染色（基于Header/Cookie/Parameter）
- 版本路由（Nacos元数据标签匹配）
- 金丝雀发布策略：百分比路由、用户维度、权重路由
- A/B测试支持
- 一致性哈希（相同用户路由到相同版本）

### 4. 日志脱敏模块
- 请求/响应日志记录
- 敏感字段脱敏（手机号、身份证号、银行卡号、密码等）
- 正则匹配和JSON字段脱敏
- 异步日志（Log4j2 AsyncAppender）
- 日志采样（高流量场景）

## 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 启动时间 | < 3秒 (JVM) / < 500ms (Native) | 从启动到接受请求 |
| QPS | > 10000 (单节点) | 4C8G配置，简单路由 |
| 内存占用 | < 200MB (JVM) / < 80MB (Native) | 空闲状态 |
| 延迟P99 | < 10ms | 网关层额外延迟 |

## 快速开始

### 环境要求
- JDK 21+
- Maven 3.9.0+
- Redis 6.0+
- Nacos 2.x（可选）

### 启动命令

```bash
# 开发环境
mvn spring-boot:run -pl easywing-platform-gateway

# 生产环境
java -XX:+UseG1GC -Xms200m -Xmx200m -jar easywing-platform-gateway.jar

# Native Image
./easywing-platform-gateway
```

### 配置示例

```yaml
easywing:
  gateway:
    jwt:
      enabled: true
      jwk-set-uri: https://auth.easywing.com/.well-known/jwks.json
      cache-ttl: 5m
      ignore-paths:
        - /actuator/**
        - /api/auth/login
    rate-limit:
      enabled: true
      default-rate: 100
      default-capacity: 200
    gray:
      enabled: true
      default-version: v1
    logging:
      enabled: true
      desensitize: true
```

## 性能测试

### 启动时间测试
```bash
time java -jar easywing-platform-gateway.jar
```

### QPS压测（使用wrk）
```bash
wrk -t12 -c400 -d30s http://localhost:8080/api/users/health
```

### 内存监控
```bash
jstat -gcutil <pid> 1000
```

### Native Image构建
```bash
mvn native:compile -Pnative -pl easywing-platform-gateway
```

## API端点

### 健康检查
```
GET /actuator/health
```

### Prometheus指标
```
GET /actuator/prometheus
```

### 网关路由
```
GET /actuator/gateway/routes
```

## 架构设计

```
请求 → JWT校验 → 限流 → 灰度路由 → 日志记录 → 下游服务
        ↓          ↓         ↓           ↓
     本地缓存   本地令牌桶  权重选择器   异步日志
```

## 许可证

MIT License
