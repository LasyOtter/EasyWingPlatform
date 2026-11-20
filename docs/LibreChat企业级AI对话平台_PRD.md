# LibreChat 企业级 AI 对话平台 · 产品开发需求文档（PRD）

| 项目 | 内容 |
| --- | --- |
| 文档编号 | PRD-LC-001 |
| 版本 | V1.0 |
| 编写日期 | 2026-11-20 |
| 文档状态 | 待评审 |
| 素材来源 | 微信公众号技术推文《推荐一个企业级 AI Agent 开源项目》<br/>https://mp.weixin.qq.com/s/OFzH3AuUjwBPrO5JuAg9TA |
| 目标读者 | 产品经理、架构师、前后端开发、测试、运维与安全合规 |

---

## 目录

1. [项目背景与目标](#一项目背景与目标)
2. [用户角色与典型场景](#二用户角色与典型场景)
3. [系统总体架构要求](#三系统总体架构要求)
4. [功能需求](#四功能需求)
5. [非功能需求](#五非功能需求)
6. [技术栈约束](#六技术栈约束)
7. [部署与交付要求](#七部署与交付要求)
8. [验收标准](#八验收标准)
9. [里程碑建议](#九里程碑建议)
10. [风险与依赖](#十风险与依赖)

---

## 一、项目背景与目标

当前企业接入大模型普遍面临三个问题：各家厂商 API 各自独立、账号权限分散；员工数据经外部 SaaS 中转，合规风险不可控；业务侧要使用 Agent、知识库、代码执行等能力时，缺少统一承载平台。本项目基于开源项目 **LibreChat** 做二次开发与私有化部署，构建一套"属于团队自己的 ChatGPT / Claude 统一入口"。

产品形态上，它本身不是一个 AI 模型，而是一套 **前端 + 后端 + 网关** 的统一系统：把 DeepSeek、千问、OpenAI、Anthropic、Google、AWS Bedrock 等几十家云端模型服务，以及 Ollama 等本地推理服务，统一到同一个 ChatGPT 风格界面，并叠加多用户账号体系、Agent、工具调用、RAG、代码执行等企业级能力。

核心目标可拆解为四条可验证的验收方向：

- **统一入口**：一套 UI 覆盖全厂商模型，消除厂商锁定，模型切换成本降到"改配置"级别。
- **数据自主**：全链路自托管，对话数据、文件、密钥不出企业内网，满足合规审计要求。
- **能力可扩展**：以 MCP + Skills + Subagents 为插件体系，业务方无需写代码即可组装 Agent。
- **工程可运维**：容器化交付、可观测性完备、支持水平扩展与多租户隔离。

---

## 二、用户角色与典型场景

| 角色 | 核心诉求 | 高频场景 |
| --- | --- | --- |
| 普通员工 | 开箱即用的对话工具 | 问答、写作、文件解读、图片理解 |
| 业务分析师 | 数据与文档处理 | 上传报表做 RAG 问答、沙箱跑 Python 分析 |
| Agent 构建者 | 无代码组装能力 | 编排 Agent、挂载 MCP 工具、共享给群组 |
| 系统管理员 | 权限与成本治理 | 用户/群组管理、模型白名单、用量与花费审计 |
| 运维 / 安全 | 稳定性与合规 | 部署升级、日志导出、租户隔离核验 |

---

## 三、系统总体架构要求

系统须采用分层解耦架构，至少包含四层：

1. **接入层**：Web 前端、API 网关、SSE 流式通道。
2. **应用层**：路由 / 控制器 / 服务 / 中间件四段式。
3. **AI 集成层**：各厂商客户端统一抽象 + MCP 协议适配。
4. **数据层**：MongoDB 主库、Redis 缓存与会话、Meilisearch 全文检索、向量库与 RAG 服务、本地或 S3 兼容对象存储。

> **架构关键点**：AI 集成层必须通过统一客户端抽象屏蔽 OpenAI / Anthropic / Azure / Bedrock / Vertex / 自定义端点之间的协议差异。新增一家厂商时只允许改动客户端适配代码与配置，**不得侵入业务服务层**。

仓库组织上要求采用 Turborepo + Bun/npm workspaces 的 monorepo 形态，`api/`、`client/`、`packages/`、`config/`、`search/`、`helm/`、`e2e/` 等目录职责清晰。

---

## 四、功能需求

### 4.1 模型接入层（P0，核心卖点）

| 编号 | 需求描述 | 优先级 |
| --- | --- | --- |
| FR-M-01 | 官方支持 OpenAI、Azure OpenAI、Anthropic（Claude）、AWS Bedrock、Google / Vertex AI、OpenAI Responses API | P0 |
| FR-M-02 | 支持 Custom Endpoints：任意 OpenAI 兼容 API 填入地址即可用，无需代理 | P0 |
| FR-M-03 | 支持本地 / 第三方接入：Ollama、AMD Lemonade、Groq、Cohere、Mistral、Apple MLX、koboldcpp、together.ai、OpenRouter、Helicone、Perplexity、DeepSeek、Qwen | P0 |
| FR-M-04 | 对话内可随时切换模型与预设；支持消息编辑、重新提交、继续生成 | P0 |
| FR-M-05 | 支持对话分支（Fork），可从任一历史消息开新分支，用于提示词 A/B 对比 | P1 |

### 4.2 Agent、MCP 与 Skills（P0）

无代码搭建自定义 Agent 是本项目的差异化能力，需提供 Agent 市场并支持按用户 / 群组维度共享。

- **MCP 工具接入**：工具接入统一走 MCP（Model Context Protocol）服务器。
- **Skills**：作为可复用的 `SKILL.md` 指令包，须支持手动触发、自动触发、常开三种模式。
- **Subagents**：需能把子任务委派给拥有独立上下文窗口的子 Agent，避免主对话上下文污染。
- **Agent 管理 API**（随 v0.8.8 beta 引入）：以 deployment-bound OIDC 身份完成机器客户端鉴权，并公开 OpenAPI 规范与 Swagger 调试页。
- **Attached Workspaces（实验性）**：允许 Agent 在托管 / 个人工作区内查看目录树、读写文件、执行 Bash。该能力**默认关闭**，需管理员显式授权。

### 4.3 代码解释器（P1）

基于开源的 ClickHouse / code-interpreter，在隔离沙箱内执行代码，须支持 **8 种语言**：Python、Node.js（JS/TS）、Go、C/C++、Java、PHP、Rust、Fortran，并支持文件上传、处理与结果下载。

沙箱必须与主服务网络隔离，执行超时与资源配额可配置。

### 4.4 生成式 UI 与 Artifacts（P1）

聊天中可直接产出 React、HTML、Mermaid 可运行产物，支持全屏预览；Mermaid 图表须能导出 SVG/PNG。

### 4.5 RAG、文件对话与联网搜索（P1）

- **Chat with Files**：需跨 OpenAI / Anthropic / Bedrock / Google 等厂商生效。
- **联网搜索**：须采用**可组合流水线**设计 —— 搜索提供商 + 内容抓取器 + 结果重排器三段可独立替换，重排器支持接入自定义 Jina Rerank。

### 4.6 图像能力（P2）

- 文生图与图生图（GPT-Image-1）、Stable Diffusion、Flux，或通过任意 MCP server 出图。
- 多模态图片理解覆盖 Claude、GPT-4o、o1、Llama-Vision、Gemini。

### 4.7 多用户与安全（P0）

- 认证须支持 **JWT、OAuth2、LDAP、邮箱登录** 四种方式。
- 提供细粒度访问控制与数据隔离、管理员面板、共享链接限流、租户隔离。
- 生产环境必须能通过配置项 `ALLOW_REGISTRATION=false` 关闭自助注册。

### 4.8 可观测性（P1）

- **Trace Viewer**：需展示模型对话顺序、工具轮次与花费。
- **日志导出**：应用日志通过 OpenTelemetry 导出并打通 Langfuse trace。
- **Context Usage 面板**：须实时显示对话 / 工具流量 / 指令 / 缓存 / 成本占用。

### 4.9 通用体验（P2）

- 多语言 UI（含简体中文、繁体中文等 17+ 种）。
- 消息全文搜索、预设与 Prompt 库、对话导出。
- 富文本复制消息、按需手动压缩上下文。

---

## 五、非功能需求

### 5.1 性能

- 首字响应依赖 SSE 流式输出，端到端首 token 延迟不得因网关层引入额外明显损耗。
- 消息全文检索在千万级文档量下响应时间控制在秒级。

### 5.2 安全

- 密钥统一由环境变量注入，**禁止硬编码**。
- 沙箱执行环境须网络隔离。
- 共享链接须限流。
- 越权访问返回统一错误码，不泄露资源存在性。

### 5.3 可用性与扩展

- 后端为无状态设计，会话与协调状态放 Redis，支持水平扩展。
- 数据库与检索服务支持独立扩缩容。

### 5.4 可维护性

- 代码须接入 ESLint flat config + Prettier、Husky Git hooks、Playwright 端到端测试与安全扫描。
- CI/CD 由 GitHub Actions 承载。

### 5.5 兼容性

- 浏览器端支持主流现代浏览器。
- 部署形态至少覆盖 Docker Compose 与 Kubernetes 两种。

---

## 六、技术栈约束

| 层次 | 技术选型要求 |
| --- | --- |
| 前端 | React + TypeScript、Tailwind CSS、Vite、模块化组件架构、react-i18next |
| 后端 | Node.js + Express（控制器 / 服务 / 中间件三层）、RESTful API、SSE 流式响应、LangChain 生态集成 |
| 数据与中间件 | MongoDB（用户 / 对话 / 配置）、Meilisearch（全文搜索）、Redis（缓存 / 会话 / 扩展协调）、向量库 + RAG API、本地或 S3 兼容存储 |
| 工程与运维 | Turborepo、Bun/npm workspaces、Docker / Docker Compose、Kubernetes Helm Chart、OpenTelemetry + Langfuse、Winston |
| 认证 | JWT、OAuth2、LDAP、邮箱验证 |

---

## 七、部署与交付要求

交付须提供三条部署路径，并配套完整文档。

### 路径一：Docker Compose（官方推荐）

**环境要求**

- Docker Engine 20.10+、Docker Compose 2.0+
- 内存 ≥ 4GB（推荐 8GB+）
- 磁盘 ≥ 20GB

**部署流程**

```bash
# 1. 克隆仓库
git clone https://github.com/danny-avila/LibreChat.git
cd LibreChat

# 2. 复制并编辑环境变量
cp .env.example .env

# 3. 生成密钥
openssl rand -hex 32   # CREDS_KEY
openssl rand -hex 32   # JWT_SECRET

# 4. 配置 .env：MONGO_URI 与厂商 API Key

# 5. 启动
docker compose up -d

# 6. 验证：/health 探针与 3080 端口
curl http://localhost:3080/health
```

**说明**

- 首次启动约拉起 6 个容器：librechat、mongodb、meilisearch、rag-api、vectordb、typesense / redis 等。
- 若需消息全文搜索，必须设置 `MEILI_MASTER_KEY`。

### 路径二：npm 本地开发

**环境要求**：Node.js 18+（以仓库 `.nvmrc` 为准）、Git、可用 MongoDB。

```bash
npm ci                # 安装依赖
npm run frontend      # 启动前端
npm run backend       # 启动后端
npm run dev           # 热更新开发模式
```

### 路径三：Kubernetes Helm

1. 先创建包含 `JWT_SECRET`、`MEILI_MASTER_KEY` 等变量的 Secret；
2. 再通过 `helm/librechat` Chart 安装到独立命名空间。

---

## 八、验收标准

| 领域 | 通过条件 |
| --- | --- |
| 模型层 | 新增一家 OpenAI 兼容端点，仅需修改配置即可在 UI 中选择并对话 |
| Agent 层 | 无代码创建一个挂载 MCP 工具的 Agent，共享给指定群组后其他成员可成功调用 |
| 沙箱层 | 8 种语言各跑通一个含文件读写的示例脚本 |
| 可观测层 | 一次多轮工具调用对话能在 Trace Viewer 中还原完整调用链与花费 |
| 部署层 | 三条路径均能在干净环境按文档一次性部署成功 |

---

## 九、里程碑建议

| 阶段 | 交付内容 | 覆盖需求 |
| --- | --- | --- |
| 第一阶段 | 基础设施与模型接入，打通统一入口 | 4.1 |
| 第二阶段 | 多用户安全体系与 Agent / MCP / Skills | 4.7、4.2 |
| 第三阶段 | RAG、代码解释器与生成式 UI | 4.3 – 4.5 |
| 第四阶段 | 可观测性强化、多语言与体验优化，完成性能与安全压测后进入生产灰度 | 4.8、4.9 |

---

## 十、风险与依赖

### 10.1 主要风险

- **上游迭代过快**：开源项目迭代极快（已跟进至 v0.8.8 并适配 GPT-6 系列模型），二次开发分支需建立定期 rebase 与回归机制。
- **实验性接口变动**：Attached Workspaces 等实验性能力接口可能变动，须做特性开关隔离。
- **多厂商协议演进**：统一客户端抽象层需保留版本兼容分支。

### 10.2 外部依赖

- 各厂商 API Key 与配额。
- LDAP / OIDC 身份源。
- 对象存储与向量库基础设施。

---

## 附录 A：关键环境变量清单

| 变量名 | 用途 | 必填 |
| --- | --- | --- |
| `CREDS_KEY` | 凭据加密密钥（`openssl rand -hex 32`） | 是 |
| `JWT_SECRET` | 会话签名密钥 | 是 |
| `MONGO_URI` | MongoDB 连接串 | 是 |
| `MEILI_MASTER_KEY` | Meilisearch 主密钥，启用全文搜索必填 | 条件必填 |
| `ALLOW_REGISTRATION` | 是否开放自助注册，生产建议 `false` | 是 |
| 各厂商 `*_API_KEY` | 模型服务访问凭据 | 按需 |

## 附录 B：术语表

| 术语 | 说明 |
| --- | --- |
| MCP | Model Context Protocol，模型与外部工具 / 数据的标准化连接协议 |
| Skills | 以 `SKILL.md` 形式封装的复用指令包，可被 Agent 按需加载 |
| Subagent | 拥有独立上下文窗口的子 Agent，用于任务委派 |
| Artifacts | 聊天中生成的 React / HTML / Mermaid 等可运行产物 |
| RAG | 检索增强生成，先检索再生成答案 |
| SSE | Server-Sent Events，服务端单向流式推送 |
| Trace Viewer | 展示对话、工具轮次与花费的调用链可视化面板 |

---

*文档结束 · PRD-LC-001 V1.0*
