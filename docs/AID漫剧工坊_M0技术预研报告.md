# AID 漫剧工坊 M0 技术预研报告

> 对应《AID漫剧工坊_EasyWing实施计划》§4.1（Week 1-2：验证关键技术可行性，降低后续风险）。
> 环境：Windows 11 / JDK 21.0.13 / Maven 3.9.16 / Spring Boot 3.5.16；实测日期 2026-11-25。

## 1. 结论速览

| POC | 目标 | 结论 | 状态 |
|---|---|---|---|
| POC-1 Provider 扩展 | 实现 1 个 ImageProvider，调通生图 | 复用既有 `AiProvider` + `ImageGenerationService` 作为四类 Provider 基座，协议层可复用 | ⚠️ 部分完成（无 AI 凭据，未真机调用上游） |
| POC-2 FFmpeg 集成 | Java 调用 FFmpeg 合成 ≥60s 视频 | 全链路打通，实测 120.02s，720p/1080p、字幕烧录、音轨对位、尾帧抽取均可用 | ✅ 通过 |
| POC-3 任务队列选型 | Redis Stream vs RocketMQ | **选 Redis Stream**：吞吐同量级，但独有消费组/ACK/PEL/故障接管语义 | ✅ 通过 |
| POC-4 前端技术栈 | Vue 3 SPA vs Next.js | **维持 Vue 3 SPA**：依赖齐备，Element Plus 覆盖所需组件 | ✅ 通过 |
| POC-5 提示词模板 | 风格块 + 色盘拼接，输出 3 示例 | 编译器落地，11 个单测通过 | ✅ 通过 |

**M0 验收对照**

| 验收项 | 结果 |
|---|---|
| 至少 1 个 AI 服务商调通（文本+图片+视频各 1 个） | ❌ 未达成——本机无任何 AI 凭据（`OPENAI_API_KEY` 等为空），无法真机调用 |
| FFmpeg 能生成 ≥60 秒合成视频 | ✅ 实测 120.02s |
| 任务队列方案敲定（建议 Redis Stream） | ✅ 已敲定 |
| 前端技术栈敲定（建议 Vue 3 SPA） | ✅ 已敲定 |

## 2. 环境约束（影响后续排期）

1. **本机 Redis 为 3.2.100 Windows 移植版，不支持 Stream**（`XADD` 返回 unknown command）。
   所有涉及 Stream 的验证必须用 Docker `redis:7-alpine`（实测镜像版本 7.4.12，映射端口 6380）。
   → 后续 aid 服务的本地联调需要把 `easywing.cache.redis.host` 指向 6380，或直接用 docker-compose 起的 7.x。
2. **无任何 AI 服务商凭据**，POC-1 只能做协议层与代码路径验证。
3. **FFmpeg 需外部安装**：本次使用 N-115858（含 libx264/libfribidi/libass/libopenjpeg/libopus），
   生产环境通过 `easywing.aid.ffmpeg.binary-path` 注入，不随仓库分发。

## 3. POC-2 FFmpeg 集成（实测数据）

### 3.1 验证链路

```
concat demuxer（清单文件）→ adelay 音轨对位 → amix 混音 → subtitles 烧录 → libx264 编码
```

由 `FfmpegService` 通过 `ProcessBuilder` 参数数组驱动（不经 shell，避免注入）。

### 3.2 关键实测结果

| 场景 | 命令要点 | 结果 |
|---|---|---|
| 双片段 concat（`-c copy` 快速路径） | `-f concat -safe 0` | exit=0，时长 14.02s |
| 混合参数重编码（时间基不一致时退化路径） | 显式 `-r`/`-pix_fmt` 对齐 | exit=0，12.04s |
| 24 段循环拼接 | 24 行清单 | **120.023220s**（满足 ≥60s） |
| 720p / 1080p 编码 | `-s 1280x720` / `1920x1080` | 均 exit=0 |
| SRT/ASS 字幕烧录 | `subtitles=` 滤镜 | 字节数 81649 vs 无字幕 67313，确认真实生效 |
| 音轨对位 | `adelay=delays=...` + `amix` | 各镜配音按偏移落位 |
| 尾帧抽取 | `-sseof -1` | 输出 1280x720 PNG，280175 bytes |
| Java 全链路 | `FfmpegService.composeTimeline` | final duration 12.0s |

### 3.3 必须固化的实现约定

- **字幕文件名必须相对化**：`ProcessBuilder.directory(workDir)` + `subtitles=subtitles-xxx.srt`。
  直接传 Windows 绝对路径（含盘符冒号）会让滤镜把 `C:` 解析成 `original_size` 选项而报错；
  `\:` 与 `\\:` 转义均无效，已实测确认。
- **`-stream_loop` 不能与短片段清单混用**：`-stream_loop 23 -i src.mp4` 会报
  `unknown keyword 'ftypisom'`。循环需求改用「清单里重复列 N 次」表达。
- **`amix` 只引用存在的音轨**：视频源若为无声渲染产物，需先确认 `[0:a]` 存在，否则
  `Stream specifier ':a' matches no streams`。
- **concat 优先 `-c copy`**，失败（时间基/编码参数不一致）时退化为显式重编码，保证时间基一致。

## 4. POC-3 任务队列选型

### 4.1 实测吞吐（同一台 Docker Redis 7.4.12）

| 方案 | 写入 | 读取 | 备注 |
|---|---|---|---|
| Redis Stream（XADD） | 27,347/s | 1,214/s（XREADGROUP） | RTT 208µs |
| Redis Stream 自动接管（XAUTOCLAIM） | — | 1,316/s | |
| List + Lua | 55,519/s | 1,240/s | 自研 processing 集 |

**结论**：单命令吞吐受 RTT 限制，两者同一量级，**性能不是决策依据**。

### 4.2 决策依据：可靠性语义

只有 Stream 内建了：

- 消费组（多消费者竞争而非广播）
- ACK 与 PEL（处理失败可留待重投，而不是丢失）
- 故障接管（旧消费者挂掉后，其 PEL 条目可被新实例 XCLAIM/XAUTOCLAIM 夺接）
- 消费位点（重启后可续跑）

这三者正是「服务重启后任务不悬挂」（FR-10.8 / NFR-1）与「失败补偿」（FR-10.5）的前提。
自研 List+Lua 需要自行维护 processing 集与扫描恢复，复杂度与出错面显著更大。

### 4.3 语义验证清单（全部实测通过）

- PEL 计数：XREADGROUP 后 pending 5000 → XACK 后 0
- 接管：XAUTOCLAIM 后 owner 由旧 consumer 转移为新 consumer
- 幂等去重：`SET key val NX EX` 首次返回 1，重复返回 0
- 批量确认：5000 条一次 XACK 归零
- 延迟调度：消息体带 `notBefore`，未到点的消息跳过本轮

### 4.4 落地形态

- 默认 `redis-stream`；无 Redis 时降级 `local`（进程内优先级队列），对应 FR-10.9 / NFR-9
- spring-data-redis 3.5 **未暴露 `XAUTOCLAIM`**（javap 确认 `autoClaim` 方法数为 0），
  故回收走 `XPENDING range` 列出挂起消息 + `XCLAIM` 逐条夺接，语义等价
- 消息体只带 `taskId`，负载从库里读，保证重启后可续跑
- 消费端 `deliveryId = "task-" + taskId` 做 SET NX 幂等窗口

## 5. POC-4 前端技术栈

**结论：维持 Vue 3 SPA**（不引入 Next.js）。

| 维度 | 现状 |
|---|---|
| 框架 | Vue 3.5 + vue-router 4.5 + Pinia |
| UI 库 | Element Plus 2.9，`el-upload` / `el-table` / `el-timeline` / `el-dialog` / `el-color-picker` 覆盖 AID 所需 |
| 国际化 | vue-i18n 9.14，已有 locales 目录 |
| 构建 | Vite 5，`/api` 代理到后端 8080，端口由 `.env.*` 的 `VITE_APP_PORT` 控制 |
| 质量门 | `pnpm type-check`（vue-tsc）、`pnpm lint`、`pnpm test`（vitest）、`pnpm build` |
| 包管理 | 必须用 `corepack pnpm@8.15.9` |

不选 Next.js 的理由：AID 是纯后台管理型 SPA，无 SEO / SSR 需求；引入 Next.js 意味着与现有
easywing-ui 技术栈分裂、鉴权与网关链路重建，收益为负。

## 6. POC-5 提示词编译器

`PromptCompiler` + `AssetRefPattern` + `VisualSnapshot` 已落地，11 个单测通过。

### 6.1 占位符语法

| 语法 | 含义 |
|---|---|
| `@资产[C01]` | 按编号引用资产 |
| `@图片1[场景参考]` / `@图片[角色主参考]` | 用户在 UI 中选择的「第 N 张图 + 名称」，归一到资产键 |

提取时按**首次出现顺序去重**（FR-11.3）：审计键去重，但正文中的多处引用保留可读标注。

### 6.2 模板占位符

```
{{style}}       风格描述主文本
{{substyles}}   子风格标签族（长提示词段落强化，FR-11.7）
{{palette}}     色盘（中文 + 十六进制，逐字引用，FR-11.6）
{{aspect}}      画幅，如 9:16
{{content}}     用户正文（占位符已替换为可读标注）
{{constraints}} 强命令式正向要求
{{negative}}    禁用词/负向清单
```

### 6.3 三条示例输出

**示例一：首帧概念图（图像）**

```
【风格】厚涂写实国风，电影级光影；国漫、奇幻
【色盘】玄青 #1A2E4A, 朱砂 #C0392B
【主体】夸父站在大荒原上 [C01:夸父·标准像(角色一致性主参考)] 手持杖
【构图】主体居中构图，保持角色五官一致
【画幅】9:16
【避免】模糊, 变形手
```

**示例二：角色动作延展（视频首帧）**

```
【风格】厚涂写实国风，电影级光影；国漫、奇幻
【色盘】玄青 #1A2E4A, 朱砂 #C0392B
【首帧】[C01:夸父·标准像(角色一致性主参考)] 在 [S01:大荒原·烈日正打(场景参考)] 上奔跑
【画幅】9:16
【镜头】跟拍，镜头随主体前移，保持角色五官一致
【避免】模糊, 变形手
```

**示例三：空镜头/转场（无主体参考）**

```
【风格】厚涂写实国风，电影级光影；国漫、奇幻
【色盘】玄青 #1A2E4A, 朱砂 #C0392B
【内容】大荒原地平线，烈日压得很低，风卷起沙尘
【画幅】9:16
【避免】模糊, 变形手
```

### 6.4 已实现的约束

- 未登记资产引用 → 告警（不静默丢弃）
- 命中禁用词 → 告警，但**不静默改写用户正文**
- 审计元数据记录风格块、色盘、画幅、快照版本、资产键、原文与所用模板
- 空快照不抛 NPE；多余空行折叠为最多一个空行

## 7. POC-1 Provider 扩展（未完成项说明）

### 7.1 已确认的可复用基础

- `AiProviderRegistry` 按 `List<AiProvider>` 注入并支持 customEndpoints 与运行时存储，天然可作为
  文本 / 图像 / 视频 / 音频 四类 Provider 的注册中心
- `ImageGenerationService` 走 OpenAI 兼容协议，可直接作为 ImageProvider 基座
- 平台侧 SSE（`Flux<ServerSentEvent<T>>`）、用量统计（`cost/`）、会话（`session/`）能力可复用

### 7.2 未达成的验收项

验收要求「至少 1 个 AI 服务商调通（文本 + 图片 + 视频各 1 个）」。本机
`OPENAI_API_KEY` / DeepSeek / 通义 / Ollama 均无可用凭据或本地服务，**无法真机验证**。

两种推进方式（择一）：

1. **提供凭据**：设置环境变量后重跑 POC-1，补齐三类 Provider 的真机调用与错误码映射
2. **先做协议层 Mock**：实现 `MockImageProvider` / `MockVideoProvider`，用固定样例验证
   编排、状态机、回调与落库链路，待凭据到位后再切换真实 Provider

### 7.3 风险提示

真实 Provider 接入的主要未知项（需在 M1 前解决）：

- 视频生成多为异步任务 + 轮询，需确认回调/轮询与本平台任务队列的衔接方式
- 图片/视频的配额与限流（当前平台只有通用 `@RateLimit`，缺少按租户的模型级配额）
- 内网环境下 `base-url` 可达性与超时配置

## 8. M0 交付物清单

**新增模块 `easywing-platform-aid`**

```
pom.xml
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
src/main/resources/application.yaml
src/main/java/com/easywing/platform/aid/
├── AidApplication.java
├── prompt/  AssetRefPattern · VisualSnapshot · PromptCompiler
├── task/    TaskMessage · TaskQueue · LocalTaskQueue · RedisStreamTaskQueue
│            AidQueueProperties · TaskQueueAutoConfiguration
└── ffmpeg/  FfmpegProperties · TimelineSegment · FfmpegService
src/test/java/com/easywing/platform/aid/
├── prompt/  AssetRefPatternTest(9) · PromptCompilerTest(11)
└── task/    LocalTaskQueueTest(9)
```

**修改的既有文件**

- `pom.xml`（根）：注册 `easywing-platform-aid` 模块
- `easywing-platform-bom/pom.xml`：登记 aid 的 dependencyManagement
- `easywing-core/.../ErrorCode.java`：新增 `AID001`~`AID012` 共 12 个错误码并登记 HTTP 状态映射

**构建状态**

- `mvn clean install -DskipTests`：30 个模块全部 SUCCESS
- `mvn test`：全绿（aid 模块 29 个测试）
- `mvn spotless:apply`：已执行

## 9. 遗留问题

1. **Redis 版本**：本机 Redis 3.2.100 不支持 Stream，本地联调需切到 Docker Redis 7.x
2. **AI 凭据缺失**：POC-1 验收项未闭环
3. **FFmpeg 分发**：需在部署文档中明确「宿主机预装 ffmpeg/ffprobe，路径经 `binary-path` 注入」
4. **`XAUTOCLAIM` 缺口**：spring-data-redis 3.5 未提供，当前用 XPENDING+XCLAIM 等价实现；
   后续升级可切回原生命令
