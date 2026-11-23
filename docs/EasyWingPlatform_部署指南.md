# EasyWingPlatform 部署指南

本文覆盖三条部署路径，对应 `LibreChat企业级AI对话平台_PRD.md` 5.5「部署层」要求：

1. **Docker Compose 全量部署**（基础设施 + 全部服务 + 监控），适合测试/演示环境；
2. **本地 Maven 开发**（按需启动服务），适合开发调试；
3. **Kubernetes Helm Chart**（`helm/easywing-platform/`），适合生产环境。

三条路径均使用同一套服务镜像与环境变量约定，密钥一律经配置/环境变量注入，仓库内不含真实凭据。

## 1. 架构与端口

| 组件 | 端口 | 说明 |
| --- | --- | --- |
| gateway | 8080 | API 网关（Spring Cloud Gateway / WebFlux），外部统一入口 |
| auth-service | 8085 | 认证服务，RS256 JWT 签发/刷新/注销 + JWKS |
| system-service | 8083 | 系统管理（用户/角色/菜单/部门/岗位/字典/日志） |
| ai-service | 8082 | AI 服务（Agent/MCP/Skills/RAG/多模态/沙箱/成本） |
| led-service | 8084 | LED 显示服务（多厂商 SDK 适配 + 节目下发 + 内码刷新） |
| mysql | 3306 | 业务库（`easywing_platform`、`easywing_system` 等） |
| redis | 6379 | 缓存、会话/技能/共享状态/令牌黑名单 |
| nacos | 8848 / 9848 | 注册与配置中心（Compose 另映射 8081 控制台） |

Compose 还包含 sentinel(8858)、minio(9000/9001)、rocketmq(9876/10911/10909)、zipkin(9411)、prometheus(9090)、grafana(3000)。仅本地开发时可用 `easywing-platform-samples/docker-compose.yml` 的精简版。

## 2. 前置条件

| 工具 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 21 | 编译与运行（GraalVM/微软构建均可） |
| Maven | 3.9+ | 构建（仓库自带 wrapper） |
| Docker | 20.10+ | Compose / 镜像构建 |
| pnpm | 8+ | 仅当需要 `easywing-ui` 前端时 |
| Helm | 3.10+ | 仅 Kubernetes 路径 |
| kubectl | 与集群版本匹配 | 仅 Kubernetes 路径 |

## 3. 路径一：Docker Compose 全量部署

### 3.1 构建镜像

镜像由多阶段 Dockerfile 构建（构建上下文为**仓库根目录**，因为 Dockerfile 内 `COPY . .` 后执行 `mvn -pl <module> -am package`）：

```bash
docker build -f easywing-platform-auth/Dockerfile    -t easywing/auth-service:1.0.0   .
docker build -f easywing-platform-system/Dockerfile  -t easywing/system-service:1.0.0 .
docker build -f easywing-platform-ai/Dockerfile     -t easywing/ai-service:1.0.0     .
docker build -f easywing-platform-gateway/Dockerfile -t easywing/gateway:1.0.0        .
docker build -f easywing-platform-led/Dockerfile     -t easywing/led-service:1.0.0     .
```

也可先本地构建再构建镜像（Dockerfile 内已含 Maven 构建阶段）：

```bash
mvn clean install -DskipTests
```

### 3.2 注入密钥

Compose 通过环境变量注入密钥。启动前设置（示例值，生产必须替换）：

```bash
export MYSQL_ROOT_PASSWORD='change-me-root-password'
export MYSQL_PASSWORD='change-me-user-password'
export NACOS_AUTH_TOKEN='<32 字节以上随机串>'
export ADMIN_PASSWORD='<管理员初始口令>'
export USER_PASSWORD='<普通用户初始口令>'
export OPENAI_API_KEY='<sk-...>'
```

敏感能力默认关闭：`ALLOW_REGISTRATION=false`、`LDAP_ENABLED=false`、`OAUTH2_ENABLED=false`、`easywing.ai.sandbox.enabled=false`、`easywing.ai.share.enabled=false`、`easywing.ai.workspace.enabled=false`。

### 3.3 启动与验证

```bash
docker-compose up -d
docker-compose ps
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8085/.well-known/jwks.json
```

- 表结构由各服务 Flyway 在启动时创建，MySQL 初始化脚本只负责建库授权；
- auth-service 首次启动会在挂载目录生成 JWT 密钥对（PEM 单文件），**必须持久化该目录**，否则重启后旧令牌全部失效；
- 若 health 长时间 not-up，先按依赖顺序排查 mysql → redis → nacos → auth → system/ai/led → gateway。

停止并清理：`docker-compose down`（加 `-v` 会删除数据卷，慎用）。

## 4. 路径二：本地 Maven 开发

```bash
# 1) 启动基础设施（mysql / redis / nacos）
docker-compose up -d mysql redis nacos

# 2) 全量构建（首次或依赖变更后）
mvn clean install -DskipTests

# 3) 按依赖顺序启动服务
cd easywing-platform-auth   && mvn spring-boot:run
cd easywing-platform-system && mvn spring-boot:run
cd easywing-platform-ai     && mvn spring-boot:run
cd easywing-platform-gateway && mvn spring-boot:run
```

- 默认 `dev` profile 使用本地/内存实现（会话、技能、用户库等），重启丢失，仅用于开发；
- 生产行为请显式使用 `prod` profile，并通过环境变量提供 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`、`JWK_SET_URI`、`ISSUER_URI`、`DEFAULT_PASSWORD`（system 服务这些变量**无默认值**，缺失会启动失败）；
- 前端（可选）：`cd easywing-ui && pnpm install && pnpm dev`，代理 `/api` 到 8080。

## 5. 路径三：Kubernetes Helm

Chart 位于 `helm/easywing-platform/`，包含 ConfigMap、Secret、MySQL/Redis/Nacos 及五个服务的 Deployment/Service，可选 PVC 与 Ingress。

### 5.1 安装

```bash
helm dependency update helm/easywing-platform      # 本 Chart 无外部依赖，可跳过
helm install easywing helm/easywing-platform \
  --set infra.mysql.rootPassword='...' \
  --set infra.mysql.password='...' \
  --set infra.nacos.authToken='...' \
  --set secrets.adminPassword='...' \
  --set secrets.userPassword='...' \
  --set secrets.openaiApiKey='...'
```

镜像默认 `easywing/<service>:1.0.0`，需提前推送到集群可访问的镜像仓库，并通过 `services.<name>.repository` / `imagePullSecrets` 调整。

### 5.2 常用 values

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `services.gateway.serviceType` | `NodePort` | 改为 `LoadBalancer` 或启用 `ingress.enabled` |
| `services.gateway.nodePort` | `30080` | NodePort 端口 |
| `ingress.enabled` / `ingress.host` / `ingress.className` | false / — / — | 启用后统一入口指向 gateway |
| `services.<name>.replicaCount` | 1 | 副本数 |
| `services.<name>.env.<KEY>` | 见 values.yaml | 追加/覆盖环境变量（如 `ALLOW_REGISTRATION`） |
| `services.auth.jwtKey.persistence.enabled` | true | 持久化 JWT 密钥，**生产必须保持开启** |
| `infra.mysql.persistence.enabled` / `infra.redis.persistence.enabled` | true | 关闭则使用 emptyDir，数据不持久 |
| `secrets.machineClients.<name>` | 注释态 | deployment-bound 机器客户端（client_credentials，scope 例：`agent-management`） |

### 5.3 验证渲染与安装结果

```bash
helm lint helm/easywing-platform
helm template easywing helm/easywing-platform --set ingress.enabled=true

kubectl get pods -n <namespace>
kubectl logs -n <namespace> deploy/auth-service
kubectl port-forward -n <namespace> svc/gateway 8080:8080
curl -s http://localhost:8080/actuator/health
```

> 注意：`NOTES.txt` 会打印内置账号与 values 中的默认口令，仅用于首次验证；生产请通过 `--set` 覆盖并限制日志可见性。

## 6. 密钥与环境变量清单

| 环境变量 | 用途 | 默认 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` / `MYSQL_USER` | MySQL root 与业务账号 | change-me-* |
| `REDIS_PASSWORD` | Redis 访问口令（为空则不启用鉴权） | 空 |
| `NACOS_AUTH_TOKEN` | Nacos 鉴权令牌（≥32 字节） | change-me-* |
| `NACOS_MYSQL_PASSWORD` | Nacos 元数据库口令 | 回退 MySQL root 口令 |
| `ADMIN_PASSWORD` / `USER_PASSWORD` / `DEFAULT_PASSWORD` | 内置账号初始口令 | change-me-* |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `GOOGLE_API_KEY` | 各 Provider 密钥 | 空 |
| `AZURE_OPENAI_API_KEY` / `AZURE_OPENAI_BASE_URL` / `AZURE_OPENAI_API_VERSION` / `AZURE_OPENAI_DEPLOYMENT` | Azure OpenAI | 空 / 2024-06-01 / gpt-4o |
| `RESPONSES_API_KEY` / `VERTEX_AI_ACCESS_TOKEN` / `BEDROCK_ACCESS_KEY` / `BEDROCK_SECRET_KEY` | 专用 Provider（默认关闭） | 空 |
| `LDAP_MANAGER_PASSWORD` / `EMAIL_SMTP_PASSWORD` | LDAP 绑定 / SMTP | 空 |
| `ALLOW_REGISTRATION` | 注册开关，默认关闭 | false |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` / `JWK_SET_URI` / `ISSUER_URI` | system 服务数据源与令牌校验 | 无默认值，必须提供 |
| `EASYWING_AUTH_JWT_KEY_PATH` | auth JWT 密钥文件路径（PEM 单文件） | `/app/keys/jwt-key.pem`（Chart） |
| `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` / `SERPER_API_KEY` / `JINA_API_KEY` | 可选增强能力 | 空 |

## 7. 排障

| 现象 | 排查方向 |
| --- | --- |
| 服务启动即退出，日志报缺少 `DATABASE_*` | system 服务未注入环境变量（prod profile 无默认值） |
| `EasyWingAuthJwtKeyNotFound` / 令牌全部 401 | JWT 密钥目录未持久化或 `EASYWING_AUTH_JWT_KEY_PATH` 指向错误 |
| Nacos 注册失败 | 检查 `NACOS_SERVER_ADDR`、`NACOS_AUTH_TOKEN` 与元数据库口令 |
| Redis 连接被拒 | 设置了 `REDIS_PASSWORD` 但客户端未配置口令 |
| AI 调用 401/403 | 确认 JWT `authoritiesClaimName` 与 Auth 签发 claim 名一致 |
| 跨服务调用 503 | 服务名是否为 `auth-service` / `system-service` / `ai-service` / `led-service`（网关 `lb://` 依赖） |
| Pod 一直 Pending | PVC 无可用 StorageClass，或 `storageClassName` 与集群不匹配 |

## 8. 生产加固清单

- [ ] 替换所有 `change-me-*` 默认口令与令牌，改用外部 Secret 管理；
- [ ] 保持 `ALLOW_REGISTRATION=false`，按需开启邮箱验证与 OAuth2/LDAP；
- [ ] 持久化 JWT 密钥目录，并备份密钥（丢失即全部令牌失效）；
- [ ] 启用 Ingress/TLS，关闭 NodePort 直曝；
- [ ] 配置 `easywing.ai.share.enabled`、`easywing.ai.workspace.enabled`、`easywing.ai.sandbox.enabled` 等敏感开关为显式声明；
- [ ] 为 MySQL/Redis 启用鉴权与网络策略，限制仅集群内访问；
- [ ] 配置 Prometheus/Grafana 告警与 Trace 导出（`easywing.ai.trace.exporter`）；
- [ ] 代码沙箱当前为进程级隔离，生产应改用容器边界。