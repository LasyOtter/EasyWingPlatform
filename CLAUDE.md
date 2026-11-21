# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

EasyWing Platform 是基于 Spring Boot 3.5 / Java 21 的企业级微服务框架，采用 Maven 多模块结构。包含自研框架层（framework + starters）、五个可运行服务（gateway / auth / system / ai / led）、示例服务（samples）以及 Vue 3 前端（easywing-ui）。

## 常用命令

### 构建

```bash
mvn clean install -DskipTests          # 全量构建（跳过测试）
mvn clean install                       # 全量构建并测试
mvn -pl <module> -am install            # 只构建某模块及其依赖
```

### 测试

```bash
mvn test                                # 单元测试（surefire，匹配 **/*Test.java、**/*Tests.java）
mvn test -Dtest=ClassName               # 单个测试类
mvn test -Dtest=ClassName#method        # 单个测试方法
mvn verify                              # 运行 failsafe 集成测试（*IT.java）
mvn verify -Pintegration-test           # 贡献指南中提到的集成测试 profile
```

### 代码质量

```bash
mvn spotless:apply                      # 自动格式化（Palantir Java Format，必须先 apply 再提交）
mvn spotless:check                      # 格式化检查（CI 会跑）
mvn checkstyle:check                    # 使用根目录 checkstyle.xml
mvn com.github.spotbugs:spotbugs-maven-plugin:check
```

### 运行服务

```bash
mvn spring-boot:run                     # 在对应模块目录下执行
```

### 前端（easywing-ui，pnpm）

```bash
pnpm install
pnpm dev            # 开发服务器（端口见 .env.* 的 VITE_APP_PORT，代理 /api 到后端 8080）
pnpm lint / pnpm lint:fix
pnpm type-check     # vue-tsc --noEmit
pnpm test           # vitest run
pnpm build          # 类型检查 + 生产构建
```

### 基础设施

```bash
docker-compose up -d                    # 根目录：MySQL/Redis/Nacos/全部服务/监控
cd easywing-platform-samples && docker-compose up -d   # 精简版
```

## Maven Profile

定义在 `easywing-platform-parent/pom.xml`：

| Profile | 用途 |
|---------|------|
| `dev`（默认激活） | 本地开发 |
| `test` | 测试环境 |
| `prod` | 生产（额外启用 source/javadoc 插件） |
| `native` | GraalVM Native Image（启用 native-maven-plugin） |
| `crac` | CRaC  Coordinated Restore at Checkpoint 支持 |

`${revision}` 为 CI-friendly 版本占位符，由 flatten-maven-plugin 在 `process-resources` 阶段解析，构建后生成 `.flattened-pom.xml`。

## 模块架构

```
easywing-platform-bom/         依赖版本统一收敛（所有第三方版本号在此）
easywing-platform-parent/      父 POM：插件管理、profile、测试依赖
easywing-platform-framework/   自研框架层（被各服务/ starter 依赖）
  easywing-core/               常量、异常体系、BizAssert 工具
  easywing-web/                RFC 9457、全局异常处理、幂等、限流切面
  easywing-security/           OAuth2 资源服务器、JWT 校验、JwtAuthConverter
  easywing-observability/      OpenTelemetry、TraceIdFilter、健康指示
easywing-platform-starters/   场景启动器（每个 starter 一个 AutoConfiguration）
  starter-web / otel / security-oauth2 / resilience4j / data / cache /
  feign / messaging / seata / gray / virtual-thread / ai
easywing-platform-gateway/     API 网关（Spring Cloud Gateway，WebFlux 响应式）
easywing-platform-auth/        认证服务（RS256 JWT 签发/刷新/注销 + JWKS）
easywing-platform-system/      系统管理服务（用户/角色/菜单/部门/岗位/字典/日志）
easywing-platform-ai/          AI 服务（LangChain4j Agent/RAG/MCP/多模态）
easywing-platform-led/         LED 显示服务（多厂商控制卡适配、节目下发、内码刷新）
easywing-platform-samples/     示例：sample-user-service、sample-order-service
easywing-ui/                   Vue 3 + Element Plus + Vite 前端
```

### 自动配置机制

**注册方式**：所有自动配置类通过 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 声明（Spring Boot 3 风格，不使用 `spring.factories`）。新增 starter / framework 模块时必须创建该文件，否则配置不生效。

**条件装配约定**：
- 类级 `@AutoConfiguration`，通过 `@ConditionalOnClass` / `@ConditionalOnWebApplication(type = SERVLET)` 控制生效范围。
- 开关属性统一走 `easywing.<模块>.enabled`，多数为 `havingValue = "true", matchIfMissing = true`（默认开启），例外见下表。
- 每个 `@Bean` 尽量加 `@ConditionalOnMissingBean`，允许使用方覆盖默认实现。
- 需要抢在官方自动配置之前/之后时用 `@AutoConfiguration(before = XxxAutoConfiguration.class)` / `after =`，例如 `data` starter `before = MybatisPlusAutoConfiguration`、`cache` starter `after = RedisAutoConfiguration`。

**各模块开关与属性前缀**：

| 模块 | 配置前缀 | 默认 | 备注 |
|------|----------|------|------|
| starter-web | — | 始终生效 | 见下方「错误处理」的双实现说明 |
| starter-otel / framework observability | `easywing.otel` | 开启 | `exporter.endpoint` 默认 `http://localhost:4317` |
| starter-security-oauth2 | `easywing.oauth2` | 开启 | `JwtDecoder` 取自 `easywing.security.oauth2.jwt.jwk-set-uri` 或 `issuer-uri` |
| framework security | `easywing.security` | 开启 | `public-paths` 放行清单；与 starter-security-oauth2 功能重叠，注意避免同时引入 |
| starter-resilience4j | `easywing.resilience` | 开启 | |
| starter-cache | `easywing.cache.type` | `multi` | 可选 `caffeine` / `multi`（本地+Redis） |
| starter-data | `easywing.data` / `easywing.datasource.dynamic` | — | 动态数据源 |
| starter-feign | `easywing.feign` | 开启 | |
| starter-messaging | `easywing.messaging` | 开启 | |
| starter-gray | `easywing.gray` | **关闭** | 需显式 `enabled=true` |
| starter-seata | `easywing.seata` | **关闭** | `matchIfMissing = false` |
| starter-virtual-thread | `spring.threads.virtual` | 开启 | 注意前缀是 `spring.*` 而非 `easywing.*` |
| starter-ai | `easywing.ai` | 开启 | 子开关 `easywing.ai.agent`（**默认关闭**）、`chat-model`、`embedding-model`、`rag`；`chat-model` 下按 `openai`/`deepseek`/`qwen`/`ollama` 的 `api-key`/`base-url` 条件创建 `ChatModel`。注意 `AiProperties` 的 `@ConfigurationProperties` 前缀是 `easywing.langchain4j`，与上面的条件注解前缀 `easywing.ai.*` **不一致**——条件装配读 `easywing.ai.*`，实际属性值读 `easywing.langchain4j.*` |
| led 服务 | `easywing.led` | 开启 | `file-config-enabled` 控制是否加载 YAML 三层配置；`base-dir` 解析 SDK dll 相对路径。数据同时落库（`led_screen` 等三表，Flyway 管理） |

**新增 starter 检查清单**：① 建 `AutoConfiguration.imports`；② 类级 `@AutoConfiguration` + 条件注解；③ `@ConfigurationProperties` 类配合 `@EnableConfigurationProperties`；④ Bean 加 `@ConditionalOnMissingBean`；⑤ 在 BOM 中登记版本（如引入新依赖）。

### 请求处理与错误规范

**端口**：Gateway `8080`、System `8083`、Auth `8085`、AI `8082`、LED `8084`、User 示例 `8081`。

**统一错误格式**：所有服务返回 RFC 9457 Problem Details（`Content-Type: application/problem+json`），标准字段 `type`/`title`/`status`/`detail`/`instance`，扩展字段 `errorCode`/`timestamp`/`traceId`/`errors`。`type` 默认形如 `https://api.easywing.io/errors/<errorCode>`。

**异常体系**（`easywing-core`）：
- `BizException` — 业务异常，携带 `errorCode`/`status`/`title`/`detail`，构造器自动从 `ErrorCode` 查表补全（`new BizException(ErrorCode.RESOURCE_NOT_FOUND, "自定义详情")`）。
- `SystemException` — 系统异常，固定 500。
- `ValidationException` — 校验异常，携带 `List<ValidationError(field, message)>`。
- Token 相关：`TokenExpiredException` / `TokenSignatureException` / `TokenBlacklistedException`。
- 原则：抛异常而非返回错误码对象；服务层统一 `throw new BizException(...)`。

**错误码**：集中在 `ErrorCode`，按 `SYS`/`BIZ`/`VAL`/`AUTH`/`SEC`/`RATE`/`IDEM` 前缀编号（如 `BIZ001` 资源不存在、`VAL001` 校验失败、`RATE001` 限流），并通过 `ERROR_MAP` 映射到 HTTP 状态码与默认标题。新增错误码需在此登记。

**异常处理器**（按服务选择）：
- `easywing-web` 提供 `GlobalExceptionHandler`（`@RestControllerAdvice`），覆盖 `BizException`/`SystemException`/`ValidationException`、Spring Validation（`@Valid`/`BindException`/`ConstraintViolation`）、认证授权（`AuthenticationException`→401、`AccessDeniedException`→403）、Resilience4j（`RequestNotPermitted`→429 带 `Retry-After: 60`、`CallNotPermittedException`→503）、`ErrorResponseException` 与兜底 `Exception`。
- `easywing-boot-starter-web` 内另有一个 `WebAutoConfiguration.GlobalExceptionHandler`（`@RestControllerAdvice`），是较早期实现（`type` 用 `easywing.io`、错误字段较简）。**引入 starter-web 会同时加载两者**，改动错误格式前先确认实际生效的处理器，避免只改一处。
- AI 服务不依赖 `easywing-web`，自行实现 `AiGlobalExceptionHandler`（`web/` 包），字段与平台对齐。
- Auth 服务有 `AuthExceptionHandler`（`exception/` 包），返回 `ProblemDetail` 但未带 `errorCode`/`traceId` 扩展字段。
- Gateway（WebFlux）**没有** `ErrorWebExceptionHandler`，`RateLimitFilter` 直接写死 `{"error":"rate_limit_exceeded",...}` 纯文本 JSON，与 RFC 9457 不一致——如需统一，需要新增网关侧的响应式错误处理。

**TraceId**：`easywing-observability` 的 `TraceIdFilter`（`Ordered.HIGHEST_PRECEDENCE + 10`）把 OTel TraceId 写入 MDC 与响应头 `X-Trace-Id`，异常处理器通过 MDC 取 `traceId`。网关侧由 `RequestIdFilter` 生成/透传 `X-Trace-Id`。

**切面**：`@RateLimit`（`RateLimitAspect`）与 `@Idempotent`（`IdempotentAspect`）均基于 Redis + Lua 脚本，仅在 `StringRedisTemplate` Bean 存在时注册。API 版本用 `@ApiVersion` 标记。

### 网关（WebFlux）

`easywing-platform-gateway` 基于 `spring-cloud-starter-gateway-server-webflux`（响应式，非 MVC），过滤器链位于 `filter/` 下：`jwt/JwtValidationFilter`、`ratelimit/RateLimitFilter`（Redis + Lua 滑动窗口）、`gray/GrayReleaseFilter`（一致性哈希流量染色）、`logging/LoggingFilter`（脱敏）。改动网关时注意保持 `Mono`/`Flux` 语义。

### 认证体系

Auth 服务用 RS256 签发 JWT，暴露 `/.well-known/jwks.json`（`JwkSetController`）；资源服务器通过 `JWK_SET_URI` 校验。注销走 Redis 黑名单（`TokenBlacklistedException`）。资源服务器侧有两套并存实现：`easywing-security` 的 `OAuth2ResourceServerConfig`（`JwtAuthConverter` 提取权限）与 `easywing-boot-starter-security-oauth2` 的 `OAuth2ResourceServerAutoConfiguration`（`@EnableMethodSecurity`，`jwk-set-uri` 优先于 `issuer-uri`）。实际接入 starter（见各服务 pom），修改鉴权行为时注意二者的 `authoritiesClaimName` 必须与 Auth 签发时的 claim 名一致。

### 数据访问

- `easywing-boot-starter-data` 集成 MyBatis-Plus + 动态数据源（`DynamicRoutingDataSource` + `DynamicDataSourceContextHolder`）。
- System 服务用 Flyway 管理 DDL（`src/main/resources/db/migration`），逻辑删除字段 `deleted`，主键策略 `assign_id`。
- 虚拟线程：`easywing-boot-starter-virtual-thread` 通过 `spring.threads.virtual.enabled=true`（默认开启）将 `AsyncTaskExecutor` 切到虚拟线程；Web 容器另需在服务配置 `server.tomcat.threads.virtual: true`。

### AI 服务

`easywing-platform-ai` 集成 LangChain4j，包结构按能力域划分（`agent/`、`tool/`、`skill/`、`rag/`、`multiagent/`、`mcp/`、`memory/`、`session/`、`multimodal/`、`sandbox/`、`cost/`、`provider/`、`websocket/`）。REST 入口集中在 `gateway/AiGatewayController`（`/api/v1/ai/**`），流式接口返回 `Flux`。Provider 通过 `AiProviderRegistry` 路由，配置属性见 `AiProperties` 与 `application-example.yml`。

### LED 显示服务

`easywing-platform-led` 把独立 LED 显示控制系统迁入平台，统一为「多厂商 SDK 适配 + 节目下发 + 内码实时刷新」三层。

- **SDK 适配**（`sdk/`）：`SdkRegistry` 按 `effectiveProvider` 选择实现，`LedSdk` 用 `default` 方法做能力分级（如 `supportsNeimaRefresh()`）。三套实现：
  - `sdk/vision/VisionSdk` — 灵信 1.0.8，`lv_led_64.dll`，句柄式下发管线（`ProgramBuilder`）。
  - `sdk/listen/ListenSdk` — 灵信五代 V2014，`ListenPlayDll.dll`，会话句柄模型（`beginSession` → `deployOne` → `SendControl` → `EndSend`）；V2014 无 `ProgramSwitch` 导出，`switchProgram` 退化为重发节目，`supportsNeimaRefresh()` 为 `false`。
  - `sdk/onbon/OnbonSdk` — 仰邦 bx06 纯 Java SDK（无 JNI），TCP 5005，支持内码刷新（`true`）。
  - `com/listenvision/*`、`cn/com/listentech/*` 是**原包名**的 JNI 本地声明（dll 未随仓库分发），加载路径由 `-Dledsystem.listen.dll` 或 `easywing.led.base-dir` 解析。
- **配置**（`config/`）：`ConfigLoader` 解析三层 YAML（`config/led.yml` + `config/screens/*.yml` + `config/refresh/*.yml`），`LedConfigProperties` 承接 `easywing.led.*`。屏与刷新配置**同时**落在 `led_screen` / `led_refresh_binding` 表，由 `LedScreenRegistry` 索引，两者通过 `screenId` 关联。
- **数据源**（`datasource/`）：SPI 注册表 + 命名源，类型 `jdbc`/`http`/`counter`/`now`/`static`/`script`，失败冷却限流（阈值 3 次 / 冷却 60s）。**`script` 在 JDK 21 不可用**（Nashorn 已移除，`getEngineByName("js")` 返回 null），`BuiltinSources.ScriptSource` 会在构造时抛 `DataSourceException`。
- **刷新引擎**（`engine/LedEngine`）：`RefreshSource` 渲染模板 → `ProgramBuilder.neimaFrame` 拼 `%disp` 帧 → SDK 下发。注意 `neimaFrame` 只拼接 `RefreshContext#renderText` 的返回值，**模板套用发生在 `RefreshSource` 层**，改协议时别在 `neimaFrame` 里重复套模板。
- **REST**：`/api/led/**`（网关路由已配），权限 `led:screen:*`；`GET /api/led/status` 在 `easywing.security.public-paths` 放行。
- **依赖注意**：onbon/uia/simple-xml/rxtx 是本地 `~/.m2` 安装的非公开 SDK，`pom.xml` 里已标 `<optional>true</optional>`，换机器构建前需重新 install。

## 代码风格

- 遵循 Google Java Style（4 空格缩进、120 列、LF），由 Spotless（Palantir）+ Checkstyle 双重约束。
- import 顺序：`java.*` → `jakarta.*` → `org.*` → `com.*` → `com.easywing.*`。
- XML/YAML 用 2 空格缩进，见 `.editorconfig`。
- Lombok + MapStruct 已在父 POM 配置注解处理器；IDE 需开启 Annotation Processing。
- 文件头保留 Apache License 版权注释（新文件沿用现有模板）。

## 提交规范

Conventional Commits：`<type>(<scope>): <subject>`。type 取 `feat|fix|docs|style|refactor|perf|test|chore|deps`，破坏性变更在 type 后加 `!`。提交前确保 `mvn spotless:apply` 与测试通过。

## 注意事项

- `AGENTS.md` 当前内容为外部记忆上下文快照，非项目规则来源；以本文件与 `README.md` 为准。
- 根 `docker-compose.yml` 会构建并启动全部服务，本地开发若只需基础设施可用 `easywing-platform-samples/docker-compose.yml`。
- MySQL 初始化脚本位于 `docker/mysql/init/`（挂载为 `/docker-entrypoint-initdb.d`），仅在数据卷首次创建时执行，只建库授权，表结构交给各服务 Flyway。
- 密钥/生产配置通过环境变量注入（如 `NACOS_AUTH_TOKEN`、`OPENAI_API_KEY`、`ADMIN_PASSWORD`），不要把默认凭据提交到仓库。
