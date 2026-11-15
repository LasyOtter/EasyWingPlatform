# EasyWing Platform

基于2025-2026企业级标准的生产就绪Java微服务框架

## 🌟 核心特性

### 必备能力 (9大核心能力)

1. **云原生支持**
   - GraalVM Native Image 支持
   - CRaC (Coordinated Restore at Checkpoint) 支持
   - Java 21+ 虚拟线程支持

2. **OpenTelemetry可观测性**
   - 分布式追踪 (Tracing)
   - 指标收集 (Metrics)
   - 日志关联 (Logging)
   - OTLP协议支持

3. **OAuth2.1/OIDC安全**
   - JWT资源服务器
   - 不透明令牌支持
   - 细粒度权限控制

4. **Resilience4j容错**
   - 熔断器 (Circuit Breaker)
   - 限流器 (Rate Limiter)
   - 重试 (Retry)
   - 隔离 (Bulkhead)

5. **声明式HTTP客户端**
   - OpenFeign集成
   - 虚拟线程支持
   - 负载均衡集成

6. **RFC 9457错误规范**
   - 标准化Problem Details响应
   - 错误码体系
   - TraceId自动注入

7. **Seata分布式事务**
   - AT模式
   - TCC模式
   - Saga模式
   - XA模式

8. **灰度发布**
   - 流量染色
   - 版本路由
   - 金丝雀发布

9. **服务网关**
   - Spring Cloud Gateway
   - 动态路由
   - 限流熔断

### 推荐能力 (10大增强能力)

- MyBatis-Plus数据访问
- 动态数据源
- 多级缓存 (Redis + Caffeine)
- 消息驱动 (RocketMQ/Kafka)
- gRPC支持
- SpringDoc OpenAPI 3.1
- Testcontainers测试支持
- Maven Archetype脚手架
- 完整示例项目
- Docker Compose开发环境

## 📦 技术栈

| 组件 | 版本 |
|------|------|
| Java | 21+ |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Spring Security | 6.5.11 |
| Nacos Client | 3.0.3 |
| OpenTelemetry | 1.59.0 |
| Resilience4j | 2.2.0 |
| Seata | 2.5.0 |
| MyBatis-Plus | 3.5.6 |
| Redisson | 3.52.0 |
| Springdoc OpenAPI | 2.9.0 |

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.9.0+
- Docker & Docker Compose

### 启动基础设施

```bash
cd easywing-platform-samples
docker-compose up -d
```

### 构建项目

```bash
mvn clean install -DskipTests
```

### 启动示例服务

```bash
# 用户服务
cd easywing-platform-samples/sample-user-service
mvn spring-boot:run

# 订单服务
cd easywing-platform-samples/sample-order-service
mvn spring-boot:run

# API网关
cd easywing-platform-gateway
mvn spring-boot:run
```

### 访问服务

- API Gateway: http://localhost:8080
- User Service: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html
- Nacos Console: http://localhost:8848/nacos
- Grafana: http://localhost:3000
- Jaeger: http://localhost:16686

## 📁 项目结构

```
EasyWingPlatform/
├── easywing-platform-bom/                 # BOM依赖版本管理
├── easywing-platform-parent/              # 父POM
├── easywing-platform-framework/           # 核心框架
│   ├── easywing-core/                     # 核心模块
│   ├── easywing-web/                      # Web模块(RFC 9457)
│   ├── easywing-observability/            # 可观测性模块
│   ├── easywing-security/                 # 安全模块
│   └── easywing-cloud/                    # 云原生模块
├── easywing-platform-starters/            # 场景启动器
│   ├── easywing-boot-starter-web/         # Web启动器
│   ├── easywing-boot-starter-otel/        # OpenTelemetry启动器
│   ├── easywing-boot-starter-security-oauth2/ # OAuth2安全启动器
│   ├── easywing-boot-starter-resilience4j/ # Resilience4j启动器
│   ├── easywing-boot-starter-data/        # 数据访问启动器
│   ├── easywing-boot-starter-cache/       # 缓存启动器
│   ├── easywing-boot-starter-feign/       # Feign启动器
│   ├── easywing-boot-starter-messaging/   # 消息驱动启动器
│   ├── easywing-boot-starter-seata/       # Seata启动器
│   ├── easywing-boot-starter-gray/        # 灰度发布启动器
│   ├── easywing-boot-starter-ai/           # AI/LLM启动器(LangChain4j)
│   └── easywing-boot-starter-virtual-thread/ # 虚拟线程启动器
├── easywing-platform-ai/                  # AI服务(Agent/RAG/MCP)
├── easywing-platform-system/              # 系统管理服务(用户/角色/权限/字典)
├── easywing-platform-gateway/             # API网关
├── easywing-platform-test/                # 测试支持
│   └── easywing-testcontainers/           # Testcontainers
├── easywing-platform-samples/             # 示例项目
│   ├── sample-user-service/               # 用户服务示例
│   ├── sample-order-service/              # 订单服务示例
│   └── docker-compose.yml                 # 本地开发环境
└── pom.xml                                # 根POM
```

## 🔧 使用指南

### 引入依赖

在项目的pom.xml中添加：

```xml
<parent>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-platform-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.easywing.platform</groupId>
            <artifactId>easywing-platform-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 使用启动器

```xml
<!-- Web + OpenAPI + RFC 9457 -->
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-web</artifactId>
</dependency>

<!-- OAuth2.1 资源服务器 -->
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-security-oauth2</artifactId>
</dependency>

<!-- OpenTelemetry -->
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-otel</artifactId>
</dependency>
```

### RFC 9457 错误响应示例

```json
{
  "type": "https://api.easywing.io/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "请求参数验证失败",
  "errorCode": "VAL001",
  "timestamp": "2024-01-15T10:30:00Z",
  "traceId": "abc123def456",
  "instance": "/api/users",
  "errors": [
    {
      "field": "email",
      "message": "邮箱格式不正确",
      "rejectedValue": "invalid-email",
      "code": "Email"
    }
  ]
}
```

## 📊 性能指标

| 指标 | JVM模式 | Native模式 |
|------|---------|------------|
| 启动时间 | < 3秒 | < 500ms |
| 内存占用 | < 200MB | < 80MB |
| 响应延迟(P99) | < 50ms | < 30ms |

## 🛠️ 开发工具

### Maven Profiles

```bash
# 开发环境
mvn spring-boot:run -Pdev

# 生产环境
mvn clean package -Pprod

# Native镜像
mvn clean package -Pnative
```

### 代码检查

```bash
# Spotless格式化
mvn spotless:check

# Spotless自动修复
mvn spotless:apply
```

## 📝 许可证

本项目基于 [MIT License](LICENSE) 开源。

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📮 联系方式

- GitHub: https://github.com/LasyOtter/EasyWingPlatform
- Email: team@easywing.io
