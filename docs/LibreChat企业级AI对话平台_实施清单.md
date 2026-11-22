# 企业级 AI 对话平台实施清单

依据 `LibreChat企业级AI对话平台_PRD.md`，按当前 Spring Boot 微服务架构拆分。仓库已有的 Provider、Agent/Tools/Skills、会话管理、OAuth2 Resource Server、Redis、Actuator 和 springdoc 能力作为基线，不另建 Node 前端，也不重写已有实现。

## P0：本次交付与优先顺序

| 顺序 | 能力 | 已有基线 | 本次实现 / 当前状态 |
| --- | --- | --- | --- |
| 1 | 模型统一接入与 Custom Endpoints | OpenAI、Anthropic、Google Provider 与统一 `AiProviderRegistry` | 增加 OpenAI-compatible 动态 Provider；支持 YAML 启动配置及管理员运行时增删，模型可进入统一列表并用于聊天。运行时变更仅存当前实例，需以配置管理持久化。 |
| 2 | 对话、模型切换与流式输出 | Chat Completions、SSE Agent、SessionManager | 修复 Provider 流式路由；OpenAI/Anthropic/Google 采用 SSE 流解析。会话增加模型、消息及租户信息；新增会话聊天、切换模型、编辑用户消息后重提、继续生成。Session API 从 JWT 取用户身份并按用户/租户隔离；修复 Redis 用户会话索引。 |
| 3 | MCP 工具 | MCP 请求/响应 DTO 与基础 HTTP Client | 增加受配置约束的服务器目录、Bearer Token、工具发现/调用 API；发现的工具注册到现有 Agent ToolRegistry，可被 Agent 调用。支持 stdio transport（`McpServerConfig.TYPE_STDIO`，子进程 JSON-RPC + 协议握手/能力协商）与 HTTP transport；服务器目录按租户与角色授权，仅授权租户可见可调用。 |
| 4 | Skills | 技能注册、执行器、推荐与链式执行 | 增加 MANUAL/AUTO/ALWAYS_ON 模式；提示词型技能可手动或自动调用模型，常开指令注入普通对话和 Agent 请求。技能管理限制管理员。技能目录持久化到内存或 Redis（`easywing.ai.skill.storage-type`），`SkillRegistry` 启动时先恢复持久化定义再播种内置技能，管理员对内置技能的启停与配置修改不会被覆盖。 |
| 5 | Subagents | 已有 `TaskDecompositionAgent`，子任务独立构造 AgentRequest | 暴露 `/subagents/delegate` API，并应用常开 Skills。 |
| 6 | 认证、安全与注册开关 | RS256 JWT、登录/刷新/吊销、角色和租户 Claim、网关限流 | 增加邮箱格式账号注册端点，默认由 `ALLOW_REGISTRATION=false` 关闭；开启后仅授予普通用户角色。新增 OAuth2/OIDC 外部登录（`easywing.auth.oauth2.enabled=true`，显式端点配置，不做 issuer 自动发现）与 LDAP 绑定认证（`easywing.auth.ldap.enabled=true`，先按过滤器搜索 DN 再绑定）；外部用户本地密码登录被拒绝。用户库持久化到 Redis（`easywing.auth.user-store=redis`），用户名/邮箱索引与播种幂等标记使多实例共享同一份用户数据；内置账号仅播种一次。邮箱验证与密码恢复流程（`TokenController` 的验证/忘记密码/重置密码端点 + 可插拔 `MailNotifier`，SMTP 经环境变量注入，默认仅记日志）。Trace/缓存/模型配置管理 API 限管理员；会话及记忆 API 根据 JWT 做用户/租户隔离。 |
| 7 | 运维与接口 | Actuator health、Prometheus、springdoc/OpenAPI | 沿用现有探针和 OpenAPI 配置；生产 profile 的会话/记忆/技能/用量账本/追踪存储切到 Redis。成本账本可持久化到 Redis（`easywing.ai.usage.storage-type=redis`），Hash 存记录本体、ZSET 按时间维护顺序并按 `max-records` 淘汰最旧记录，多实例共享同一份 Token/成本视图。 |

## 后续优先级

### P0 尚缺的生产集成

- OpenAI Responses API 已提供专用 Provider（`OpenAiResponsesProvider`，`easywing.ai.responses.enabled=true` 开启，`POST /v1/responses`，`input`/`output_text`/`max_output_tokens`/`response.output_text.delta` 事件均适配，默认关闭，密钥仅经 `RESPONSES_API_API_KEY` 等环境变量注入）。Vertex AI（Gemini on Vertex）已提供专用 Provider（`VertexAiProvider`，`easywing.ai.vertex.enabled=true` 开启，OAuth2 访问令牌鉴权，`easywing.ai.vertex.base-url` 指向 `.../publishers/google/models`，请求体与响应解析与 Google AI Studio 共用 `GeminiProtocol`，默认关闭，令牌仅经 `VERTEX_AI_ACCESS_TOKEN` 注入）。AWS Bedrock 已提供专用 Provider（`BedrockAiProvider`，`easywing.ai.bedrock.enabled=true` 开启，`BedrockProtocol` 适配 Converse 线协议，走 `BedrockRuntimeAsyncClient` 流式，支持 `toolChoice`/`StopReason`/`TokenUsage` 映射，默认关闭，凭据仅经 `BEDROCK_ACCESS_KEY`/`BEDROCK_SECRET_KEY` 等环境变量注入）。当前可通过 OpenAI-compatible 自定义端点接入兼容服务，Azure OpenAI 已有专用 Provider。
- 多实例共享的 Trace：已完成。DebugService 改为经 `TraceStore` 读写追踪快照，`InMemoryTraceStore` 为默认本地实现，`RedisTraceStore`（`easywing.ai.trace.storage-type=redis`，生产 profile 默认）用 Hash（`ai:trace`）存本体、ZSET（`ai:trace:ts`）按开始时间维护顺序并按 `max-records`（下限 100）淘汰最旧记录，多个 AI 实例共享同一份 Agent 执行链路，`/debug/traces` 查询到的是全局数据。仍未接 OpenTelemetry/Langfuse 导出，如需导出可再扩展 `TraceStore` 或新增导出器。
- 技能/用户的管理界面（本阶段不创建独立前端工程，能力经 REST API 提供）。

### P1

- 对话 Fork/分支及分支差异比较：已完成。`ConversationSession` 增加 `parentSessionId`/`forkedAtMessageIndex`；`SessionForkService`（经 `SessionManager`，内存与 Redis 存储通用）提供 `fork`（按消息下标复制，越界自动收敛，消息对象深拷贝避免跨会话别名）、`branches`（按用户+租户过滤直接子会话）、`diff`（按消息下标逐位对齐，产出 `UNCHANGED`/`EDITED`/`ADDED`/`REMOVED` 条目与计数）。端点：`POST /api/v1/ai/sessions/{id}/fork`、`GET /api/v1/ai/sessions/{id}/branches`、`GET /api/v1/ai/sessions/{id}/diff?otherSessionId=`，均以 JWT 用户与租户做归属校验。
- RAG 与文件对话：已完成。`RagService.Document` 增加 `userId`/`tenantId` 归属，入库时写入向量块 metadata；`RagRequest` 增加 `userId`/`tenantId`，`query()` 改用 `searchWithFilter` 并合并请求 `filter` 与归属（修复此前 `filter` 字段未生效的缺陷）。新增 `DocumentRecord`/`DocumentStore`/`InMemoryDocumentStore` 文档目录（与向量存储分离，支撑按用户/租户列举与删除，进程内存实现，重启丢失）。新增 `FileDocumentService`：白名单扩展名（文本类 40+ 种）、单文件 5MB、解码后 100 万字符上限、NUL 二进制拒绝、UTF-8 校验、文件名 basename 防穿越、归属校验与级联删除。端点：`POST /api/v1/ai/rag/documents/upload`（multipart `file` + 可选 `title`）、`GET /api/v1/ai/rag/documents`、`GET /api/v1/ai/rag/documents/{documentId}`、`DELETE /api/v1/ai/rag/documents/{documentId}`；`/rag/query` 在携带 JWT 时按用户与租户限定检索范围。`spring.servlet.multipart` 限制 5MB/6MB。
- 隔离代码沙箱：已有 `CodeExecutionSandbox`（`sandbox/`，支持 Python/JavaScript/Java/Go，临时目录隔离 + 超时 + 输出上限 + 安全字符检查），有测试覆盖。
- 联网检索流水线：已完成。新增 `search/` 包，三段可替换接口 `SearchProvider` / `ContentFetcher` / `ResultReranker`（默认 `IdentityReranker`）；`SerperSearchProvider`（`POST {base}/search`，`X-API-KEY`，解析 `organic[]`）、`HtmlContentFetcher`（仅 http/https，拒绝 loopback/链路本地/IPv6 ULA 等内网地址，HTML→纯文本，大小与字符上限）、`JinaReranker`（`POST {base}/rerank`，`Bearer`，按 `relevance_score` 回写 score）；`WebSearchPipeline` 编排：关闭返回 404、无 provider 返回 400、按 `fetchLimit` 保序抓取、重排、可选经 `AiProviderRegistry` 生成答案（上下文上限 6000 字符）。配置 `easywing.ai.web-search.*` 默认关闭，密钥仅经 `SERPER_API_KEY`/`JINA_API_KEY` 注入。端点 `POST /api/v1/ai/search`。
- Artifacts：已完成。新增 `artifact/` 包：`ArtifactExtractor` 从会话消息提取 `html` / `react` / `mermaid` 围栏代码块（跳过不支持语言与过短代码），`Artifact`（类型 HTML/REACT/MERMAID + 确定性 id），`ArtifactService` 按会话归属（userId + 规范化租户）列举/读取，并导出 Mermaid 为自包含 SVG（`foreignObject` + mermaid.js CDN，XML 转义）；PNG 导出明确返回 400（需浏览器渲染）。端点 `GET /api/v1/ai/sessions/{id}/artifacts`、`GET .../artifacts/{artifactId}`、`GET .../artifacts/{artifactId}/export`。

### P2

- Web 对话 UI、多语言、全文搜索、导出、Prompt 库和上下文用量面板。按仓库约束，本阶段不创建独立前端工程。

## 关键配置

- `easywing.ai.custom-endpoints.<id>`：`enabled`、`base-url`、`api-key`、`models`。
- `easywing.ai.mcp.servers.<name>` 与 `easywing.ai.mcp.server-tokens.<name>`：MCP HTTP 地址及可选 Bearer Token；`type=stdio` 时用 `command`/`args` 启动子进程。
- `easywing.ai.session.storage-type=redis`、`easywing.ai.memory.type=redis`、`easywing.ai.skill.storage-type=redis`：生产 Redis 会话/记忆/技能目录。
- `easywing.auth.user-store=redis`：共享用户库；`memory` 仅用于本地开发（默认，重启丢失）。
- `ALLOW_REGISTRATION=false`：默认关闭注册。
- `easywing.auth.oauth2.*` / `easywing.auth.ldap.*`：默认关闭，密钥经环境变量注入。
