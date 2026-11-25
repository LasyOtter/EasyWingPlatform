# AID 漫剧工坊 — EasyWingPlatform 实施计划 v1.0

> **文档性质**：基于 `AID漫剧工坊_开发需求文档_最优版.md` 的技术实施计划  
> **目标平台**：EasyWingPlatform（Spring Boot 3 + Vue 3 + 多租户 + AI 能力）  
> **编制日期**：2025-01-XX  
> **状态**：待评审

---

## 📋 目录

- [1. 执行摘要](#1-执行摘要)
- [2. 技术适配性分析](#2-技术适配性分析)
- [3. 架构设计](#3-架构设计)
- [4. 分阶段实施计划](#4-分阶段实施计划)
- [5. 数据模型设计](#5-数据模型设计)
- [6. 技术风险与对策](#6-技术风险与对策)
- [7. 资源与时间估算](#7-资源与时间估算)
- [8. 验收与交付](#8-验收与交付)

---

## 1. 执行摘要

### 1.1 项目目标

在 EasyWingPlatform 基础上构建 **AID 漫剧工坊**，提供从剧本到成片的端到端 AI 漫剧/短剧创作能力。

**核心价值**：
- 复用 EasyWing 现有的多租户、权限、AI 模型管理、SSE 流式基础设施
- 新增视频创作领域能力：项目管理、资产库、分镜编辑、视频生成、配音合成、成片导出
- 形成"通用 AI 能力平台 + 垂直创作场景"的产品矩阵

### 1.2 技术栈对齐

| 层次 | AID 原始技术栈 | EasyWingPlatform | 适配方案 |
|---|---|---|---|
| 后端框架 | Spring Boot 2.x + RuoYi | Spring Boot 3.x + 自研架构 | ✅ 直接对齐，更现代 |
| 前端用户端 | Next.js 14 (SSG) | 无 | ⚠️ **需新建**，或改为 Vue 3 SPA |
| 前端管理端 | Vite + Vue 3 (RuoYi) | Vite + Vue 3 + Element Plus | ✅ 技术栈一致，UI 组件复用 |
| 多租户 | 无（单租户） | ✅ 原生支持 | ✅ 天然优势，支持 SaaS 化 |
| AI 能力 | 自建 Provider 层 | ✅ 已有模型管理 + 对话 | ✅ 扩展四类 Provider（文本/图片/视频/音频） |
| 任务队列 | RocketMQ（可选） | 无 | ⚠️ 新增模块，或用 Spring Task + Redis |
| 文件存储 | 本地 + OSS | ✅ 本地 + MinIO | ✅ 对齐 |
| 数据库 | MySQL 8 | MySQL 8 / PostgreSQL | ✅ 对齐 |
| 缓存 | Redis | Redis | ✅ 对齐 |

**核心决策点**：
1. **用户端技术选型**：Next.js（保留 SEO）vs Vue 3 SPA（统一技术栈）→ **建议 Vue 3 SPA**，降低维护成本
2. **任务队列**：RocketMQ vs Spring @Async + Redis Stream → **建议 Redis Stream**，部署更轻量
3. **模块拆分**：独立 `easywing-platform-aid` 模块 vs 集成到现有模块 → **建议独立模块**，职责清晰

### 1.3 关键里程碑（6 个月计划）

| 阶段 | 时间 | 交付物 | 验收标准 |
|---|---|---|---|
| **M0 技术预研** | Week 1-2 | 技术选型报告、POC | Provider 扩展 POC、任务队列选型 |
| **M1 骨架搭建** | Week 3-6 | 项目/资产/视觉设定模块 | AC-11（视觉设定可导出） |
| **M2 分镜闭环** | Week 7-10 | 剧本→分镜→图→视频单镜 | AC-1（单镜端到端） |
| **M3 音画成片** | Week 11-14 | 配音、时间轴、导出 | AC-1（完整版） |
| **M4 商业化** | Week 15-18 | 计费、用户中心 | AC-4（计费一致性） |
| **M5 部署与优化** | Week 19-22 | 一键部署、性能优化 | AC-6/7（部署与回滚） |
| **M6 打磨上线** | Week 23-26 | 质量门、监控、文档 | 全部 AC 通过 |

---

## 2. 技术适配性分析

### 2.1 EasyWing 现有能力评估

#### ✅ 可直接复用的能力

| 能力 | 现有实现 | 用途 |
|---|---|---|
| 多租户隔离 | `TenantContextHolder` + `@TenantId` 拦截器 | 项目/资产/任务按租户隔离 |
| 用户权限 | Spring Security + RBAC | 用户/管理员权限控制 |
| AI 模型管理 | `easywing-platform-ai` + ModelService | 扩展为四类 Provider |
| SSE 流式 | `/chat/completions/stream` | 任务进度推送 |
| 文件上传 | MinIO + 本地存储 | 视频/图片/音频存储 |
| i18n | vue-i18n + 后端 MessageSource | 国际化支持 |
| 审计日志 | 操作日志 AOP | 资产变更追溯 |

#### ⚠️ 需要扩展的能力

| 能力 | 现状 | 扩展方案 |
|---|---|---|
| **AI Provider 层** | 仅支持文本对话模型 | 新增 `ImageProvider`、`VideoProvider`、`AudioProvider` 接口 |
| **异步任务框架** | 无统一任务框架 | 新建 `TaskService`，支持状态机、重试、暂停/恢复 |
| **长文本处理** | 对话场景，上下文 ≤128K | 剧本可能超长，需分段 + 摘要 |
| **文件版本管理** | 无版本概念 | 资产/视觉设定需快照版本 |
| **WebSocket 推送** | 无（仅 SSE） | 任务进度推送，SSE 可满足 |

#### ❌ 需要新建的能力

| 能力 | 说明 |
|---|---|
| **视频编辑引擎** | 时间轴、音画同步、字幕叠加 → 调用 FFmpeg |
| **分镜脚本 DSL** | 分镜描述的结构化表达 → JSON Schema |
| **提示词模板引擎** | 风格块 + 色盘 + 场景描述 → Freemarker/自定义 |
| **计费引擎** | 预冻结/结算/账单 → 新建 `BillingService` |
| **流程画布（可选）** | 可视化编排 → M5 后考虑 |

### 2.2 技术债务与改造成本

| 项 | 现状 | 目标 | 改造成本 | 优先级 |
|---|---|---|---|---|
| AI 模型配置 | 单表 `ai_model` | 四类 Provider + 能力矩阵 | **中**（3-5 天） | P0 |
| 文件存储路径 | 扁平化 | 按租户/项目/资产分层 | **低**（1-2 天） | P1 |
| 任务幂等性 | 无统一实现 | 分布式锁 + 任务 ID 去重 | **中**（2-3 天） | P0 |
| 前端路由 | 单页应用 | 新增用户端路由组 | **高**（5-10 天，如选 Vue）| P0 |

### 2.3 第三方依赖评估

| 依赖 | 用途 | 许可证 | 风险 |
|---|---|---|---|
| FFmpeg | 视频剪辑/合成 | LGPL 2.1+ | ✅ 商用友好（动态链接） |
| marked + DOMPurify | Markdown 渲染（已有） | MIT | ✅ 无风险 |
| RocketMQ（可选） | 任务队列 | Apache 2.0 | ✅ 可选，建议用 Redis |
| Hutool | 工具库（已有） | Apache 2.0 | ✅ 无风险 |

---

## 3. 架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户创作端 (Vue 3 SPA)                   │
│  - 项目管理  - 剧本编辑  - 分镜设计  - 生成监控  - 成片导出   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS + SSE
┌───────────────────────────┴─────────────────────────────────┐
│                      管理后台 (Vue 3 + Vite)                  │
│  - AI 模型配置  - 用户管理  - 计费管理  - 系统监控           │
└───────────────────────────┬─────────────────────────────────┘
                            │ REST API + WebSocket
┌───────────────────────────┴─────────────────────────────────┐
│                   EasyWing Gateway (Spring Cloud Gateway)     │
│  - 路由  - 鉴权  - 限流  - 日志                              │
└────┬────────────────┬────────────────┬────────────────┬─────┘
     │                │                │                │
┌────▼─────┐  ┌───────▼──────┐  ┌────▼──────┐  ┌─────▼──────┐
│ AI 模块   │  │ AID 创作模块  │  │ 系统模块   │  │ 文件模块   │
│ (已有)    │  │ (新建)       │  │ (已有)    │  │ (已有)     │
│- 对话     │  │- 项目管理    │  │- 用户     │  │- MinIO    │
│- 模型管理 │  │- 资产库      │  │- 权限     │  │- 本地存储  │
│          │  │- 分镜编辑    │  │- 租户     │  │           │
│          │  │- 视频生成    │  │- 审计     │  │           │
│          │  │- 配音合成    │  │          │  │           │
│          │  │- 任务调度    │  │          │  │           │
└──────────┘  └──────┬───────┘  └──────────┘  └────────────┘
                     │
          ┌──────────┴───────────┐
          │  Provider 适配层      │
          │ - TextProvider       │
          │ - ImageProvider      │
          │ - VideoProvider      │
          │ - AudioProvider      │
          └──────────┬───────────┘
                     │
          ┌──────────┴───────────────────────────┐
          │   第三方 AI 服务                      │
          │  OpenAI / 通义 / 文心 / MiniMax ...  │
          └──────────────────────────────────────┘
```

### 3.2 模块结构设计

```
easywing-platform-aid/
├── aid-api/                      # API 接口定义
│   ├── domain/
│   │   ├── Project.java         # 项目实体
│   │   ├── Episode.java         # 分集
│   │   ├── Asset.java           # 资产
│   │   ├── Shot.java            # 分镜
│   │   ├── VisualConfig.java    # 视觉设定快照
│   │   └── GenerationTask.java  # 生成任务
│   └── dto/
│       ├── ProjectCreateReq.java
│       ├── ShotGenerateReq.java
│       └── TaskProgressEvent.java
├── aid-service/                  # 业务逻辑
│   ├── service/
│   │   ├── ProjectService.java
│   │   ├── AssetService.java
│   │   ├── ShotService.java
│   │   ├── GenerationService.java  # 核心生成编排
│   │   ├── PromptService.java      # 提示词拼接
│   │   └── TaskService.java        # 任务状态机
│   └── provider/
│       ├── ImageProvider.java      # 图片生成接口
│       ├── VideoProvider.java      # 视频生成接口
│       ├── AudioProvider.java      # 音频生成接口
│       └── impl/
│           ├── OpenAIImageProvider.java
│           ├── MinimaxVideoProvider.java
│           └── ...
├── aid-integration/              # 第三方集成
│   ├── ffmpeg/
│   │   └── VideoEditorService.java
│   └── oss/
│       └── AssetStorageService.java
└── aid-web/                      # Web 层
    └── controller/
        ├── ProjectController.java
        ├── ShotController.java
        └── TaskController.java
```

### 3.3 核心流程设计

#### 3.3.1 分镜生成流程（同步 + 异步混合）

```
用户提交分镜生成请求
    ↓
1. 同步阶段（≤ 200ms）
   - 参数校验
   - 权限检查
   - 预冻结余额
   - 创建任务记录（status=PENDING）
   - 返回 taskId
    ↓
2. 异步阶段（提交到任务队列）
   ├─→ 拼接提示词（视觉设定 + 风格块 + 场景描述）
   ├─→ 调用 ImageProvider 生成分镜图
   │   └─→ 轮询/回调获取结果
   ├─→ 存储图片到 OSS
   ├─→ 调用 VideoProvider 生成视频
   │   └─→ 轮询/回调获取结果
   ├─→ 存储视频到 OSS
   ├─→ 更新任务状态（status=COMPLETED）
   └─→ 结算费用（释放冻结，扣除实际消费）
    ↓
3. 进度推送（SSE）
   - 每个阶段变化时推送事件
   - 客户端实时更新 UI
```

#### 3.3.2 任务状态机

```
           ┌─→ PENDING ──→ RUNNING ──→ COMPLETED
           │                 │              ↑
创建任务 ──┤                 ↓              │
           │              PAUSED ───────────┘
           │                 ↓
           └────────────→ FAILED
                            │
                            ↓
                        (可重试)
```

**状态转换规则**：
- `PENDING → RUNNING`：任务被调度器拾取
- `RUNNING → PAUSED`：用户主动暂停
- `RUNNING → FAILED`：重试 3 次后仍失败
- `RUNNING → COMPLETED`：所有子任务成功
- `FAILED → PENDING`：用户手动重试

---

## 4. 分阶段实施计划

### 4.1 M0 技术预研（Week 1-2）

#### 目标
验证关键技术可行性，降低后续风险。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| POC-1：Provider 扩展 | 后端 | 实现 1 个 ImageProvider（如通义万相），调通生图 | 2 天 |
| POC-2：FFmpeg 集成 | 后端 | Java 调用 FFmpeg 合成 1 段测试视频 | 2 天 |
| POC-3：任务队列选型 | 后端 | Redis Stream vs RocketMQ 性能对比报告 | 2 天 |
| POC-4：前端技术栈确认 | 前端 | Vue 3 SPA vs Next.js 原型对比 | 2 天 |
| POC-5：提示词模板 | 算法 | 设计风格块 + 色盘拼接逻辑，输出 3 个示例 | 2 天 |

#### 验收标准
- [ ] 至少 1 个 AI 服务商调通（文本 + 图片 + 视频各 1 个）
- [ ] FFmpeg 能生成 ≥60 秒的合成视频
- [ ] 任务队列方案敲定（建议：Redis Stream）
- [ ] 前端技术栈敲定（建议：Vue 3 SPA）

### 4.2 M1 骨架搭建（Week 3-6）

#### 目标
搭建项目、资产、视觉设定的基础数据模型与 CRUD 能力。

#### 数据库表设计（核心表）

```sql
-- 项目表
CREATE TABLE aid_project (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    visual_config_snapshot JSON,  -- 视觉设定快照
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) COMMENT='创作项目';

-- 分集表
CREATE TABLE aid_episode (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    episode_no INT NOT NULL,
    title VARCHAR(100),
    script TEXT,  -- 剧本内容
    duration INT,  -- 目标时长（秒）
    status VARCHAR(20),
    created_at DATETIME,
    FOREIGN KEY (project_id) REFERENCES aid_project(id),
    INDEX idx_project (project_id)
) COMMENT='分集';

-- 资产表
CREATE TABLE aid_asset (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    asset_code VARCHAR(50) NOT NULL,  -- 资产编号（如 C01）
    asset_type VARCHAR(20),  -- CHARACTER/SCENE/PROP
    name VARCHAR(100),
    description TEXT,
    visual_reference_url VARCHAR(500),  -- 参考图
    prompt_template TEXT,  -- 提示词模板
    version INT DEFAULT 1,
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY uk_project_code (project_id, asset_code),
    INDEX idx_tenant (tenant_id)
) COMMENT='资产库';

-- 分镜表
CREATE TABLE aid_shot (
    id BIGINT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    shot_no INT NOT NULL,
    description TEXT,  -- 画面描述
    dialogue TEXT,  -- 台词
    camera_movement VARCHAR(100),  -- 镜头语言
    duration INT,  -- 时长（秒）
    image_url VARCHAR(500),  -- 分镜图
    video_url VARCHAR(500),  -- 视频
    prompt_final TEXT,  -- 最终提示词（审计用）
    status VARCHAR(20),
    created_at DATETIME,
    FOREIGN KEY (episode_id) REFERENCES aid_episode(id),
    INDEX idx_episode (episode_id)
) COMMENT='分镜';

-- 生成任务表
CREATE TABLE aid_generation_task (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(20),  -- IMAGE/VIDEO/AUDIO
    target_id BIGINT,  -- 关联的 shot_id 或其他
    provider VARCHAR(50),  -- 使用的 Provider
    model VARCHAR(100),
    prompt TEXT,
    status VARCHAR(20),  -- PENDING/RUNNING/COMPLETED/FAILED
    progress INT DEFAULT 0,
    result_url VARCHAR(500),
    error_message TEXT,
    cost_amount DECIMAL(10, 4),  -- 实际费用
    retry_count INT DEFAULT 0,
    created_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status),
    INDEX idx_target (target_id)
) COMMENT='生成任务';
```

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 后端-数据模型 | 后端 | 上述 5 张核心表 + JPA Entity | 3 天 |
| 后端-CRUD 接口 | 后端 | 项目/资产/分集 CRUD 接口 | 3 天 |
| 后端-视觉设定快照 | 后端 | 视觉设定导出/导入接口 | 2 天 |
| 前端-项目管理页 | 前端 | 项目列表/创建/编辑页面 | 3 天 |
| 前端-资产库页 | 前端 | 资产列表/上传参考图/编辑 | 3 天 |
| 前端-视觉设定页 | 前端 | 风格块/色盘编辑器 | 3 天 |
| 集成测试 | QA | 端到端创建 1 个项目并添加 3 个资产 | 2 天 |

#### 验收标准（AC-11）
- [ ] 创建项目时可配置视觉设定（风格块 + 色盘）
- [ ] 视觉设定可导出为 JSON 模板
- [ ] 新项目可导入模板，配置生效
- [ ] 资产编号支持自定义（如 C01、S01）

### 4.3 M2 分镜闭环（Week 7-10）

#### 目标
实现 **剧本 → 分镜 → 图片 → 视频** 单镜完整链路。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 后端-Provider 层 | 后端 | ImageProvider + VideoProvider 接口及 2 个实现 | 4 天 |
| 后端-提示词服务 | 后端 | PromptService（风格块 + 色盘 + 场景描述拼接） | 3 天 |
| 后端-任务调度 | 后端 | TaskService + Redis Stream 消费者 | 4 天 |
| 后端-分镜生成接口 | 后端 | POST /api/v1/aid/shots/generate | 3 天 |
| 前端-分镜编辑器 | 前端 | 分镜列表/编辑器/生成按钮 | 4 天 |
| 前端-进度监控 | 前端 | SSE 接收任务进度并更新 UI | 3 天 |
| 集成测试 | QA | 端到端生成 1 个分镜（图+视频） | 2 天 |

#### 核心代码示例

**ImageProvider 接口**：
```java
public interface ImageProvider {
    /**
     * 提交图片生成任务
     * @return 任务 ID（用于轮询）
     */
    String submitImageGeneration(ImageGenerationRequest request);
    
    /**
     * 查询任务状态
     */
    ImageGenerationResult queryTask(String taskId);
    
    /**
     * 获取 Provider 能力
     */
    ProviderCapability getCapability();
}

public class TongyiImageProvider implements ImageProvider {
    @Override
    public String submitImageGeneration(ImageGenerationRequest request) {
        // 调用通义万相 API
        // ...
        return taskId;
    }
    
    @Override
    public ImageGenerationResult queryTask(String taskId) {
        // 轮询任务状态
        // ...
    }
}
```

**任务调度（Redis Stream）**：
```java
@Service
public class TaskScheduler {
    @StreamListener(target = "aid:tasks", id = "task-consumer")
    public void processTask(GenerationTask task) {
        try {
            // 1. 更新状态为 RUNNING
            taskService.updateStatus(task.getId(), TaskStatus.RUNNING);
            
            // 2. 调用对应 Provider
            Provider provider = providerFactory.get(task.getProvider());
            String result = provider.generate(task.getPrompt());
            
            // 3. 存储结果
            String url = storageService.upload(result);
            taskService.complete(task.getId(), url);
            
            // 4. 推送进度
            sseService.push(task.getTenantId(), TaskProgressEvent.completed(task.getId()));
        } catch (Exception e) {
            taskService.fail(task.getId(), e.getMessage());
            // 自动重试逻辑
        }
    }
}
```

#### 验收标准（AC-1 单镜版）
- [ ] 输入一段剧本（≥100 字）
- [ ] 生成 1 个分镜图（≥512×512）
- [ ] 生成 1 个视频片段（≥5 秒）
- [ ] 进度实时推送（延迟 ≤1 秒）

### 4.4 M3 音画成片（Week 11-14）

#### 目标
实现配音、时间轴对齐、成片导出。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 后端-AudioProvider | 后端 | TTS 接口及 1 个实现（如 Azure TTS） | 3 天 |
| 后端-FFmpeg 服务 | 后端 | 视频拼接/配音/字幕叠加 | 4 天 |
| 后端-导出接口 | 后端 | POST /api/v1/aid/episodes/export | 3 天 |
| 前端-时间轴编辑器 | 前端 | 可拖拽调整分镜顺序与时长 | 5 天 |
| 前端-配音配置 | 前端 | 选择音色/调整语速/试听 | 3 天 |
| 前端-导出页 | 前端 | 选择格式/分辨率/导出按钮 | 2 天 |
| 集成测试 | QA | 端到端导出 1 个 ≥60 秒的成片 | 3 天 |

#### FFmpeg 集成示例

```java
@Service
public class VideoEditorService {
    public String mergeVideoClips(List<String> videoUrls, List<AudioTrack> audioTracks) {
        // 1. 下载所有视频到临时目录
        List<File> videos = videoUrls.stream()
            .map(url -> downloadTemp(url))
            .collect(Collectors.toList());
        
        // 2. 生成 FFmpeg 滤镜复杂图
        String filterComplex = buildFilterComplex(videos, audioTracks);
        
        // 3. 执行 FFmpeg
        String outputPath = "/tmp/output_" + UUID.randomUUID() + ".mp4";
        String cmd = String.format(
            "ffmpeg -i %s -filter_complex '%s' -c:v libx264 -c:a aac %s",
            String.join(" -i ", videos.stream().map(File::getPath).toList()),
            filterComplex,
            outputPath
        );
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
        pb.start().waitFor();
        
        // 4. 上传到 OSS
        String finalUrl = ossService.upload(new File(outputPath));
        
        // 5. 清理临时文件
        videos.forEach(File::delete);
        new File(outputPath).delete();
        
        return finalUrl;
    }
}
```

#### 验收标准（AC-1 完整版）
- [ ] 输入一段剧本（≥500 字）
- [ ] 完成 ≥5 个分镜的生成
- [ ] 配音覆盖所有台词
- [ ] 导出 1 个 ≥60 秒的成片文件（MP4）
- [ ] 视频画质 ≥720p

### 4.5 M4 商业化（Week 15-18）

#### 目标
实现计费、用户额度、账单管理。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 后端-计费引擎 | 后端 | BillingService（预冻结/结算/退款） | 5 天 |
| 后端-额度管理 | 后端 | 用户额度表 + 充值接口 | 3 天 |
| 后端-账单查询 | 后端 | 账单列表/详情接口 | 2 天 |
| 前端-额度中心 | 前端 | 额度展示/充值记录/消费记录 | 3 天 |
| 前端-报价预览 | 前端 | 生成前显示预估费用 | 2 天 |
| 管理端-计费配置 | 前端 | 配置各模型的单价 | 3 天 |
| 集成测试 | QA | 验证报价=结算，余额不足拦截 | 2 天 |

#### 计费流程设计

```
用户点击生成
    ↓
1. 调用报价接口（不产生任务）
   - 根据分镜数量 × 模型单价计算
   - 返回预估金额
    ↓
2. 用户确认后提交
   - 预冻结金额（余额 - 冻结金额）
   - 创建任务
    ↓
3. 任务执行完成
   - 获取实际消耗 tokens
   - 计算实际金额
   - 结算：扣除实际金额，释放差额
    ↓
4. 任务失败
   - 全额释放冻结金额
```

#### 数据库表设计

```sql
-- 用户额度表
CREATE TABLE aid_user_balance (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(10, 2) DEFAULT 0,  -- 可用余额
    frozen_amount DECIMAL(10, 2) DEFAULT 0,  -- 冻结金额
    total_consumed DECIMAL(10, 2) DEFAULT 0,  -- 累计消费
    updated_at DATETIME,
    version INT DEFAULT 0  -- 乐观锁
) COMMENT='用户额度';

-- 账单表
CREATE TABLE aid_billing_record (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_id BIGINT,
    type VARCHAR(20),  -- CHARGE/CONSUME/REFUND
    amount DECIMAL(10, 4),
    before_balance DECIMAL(10, 2),
    after_balance DECIMAL(10, 2),
    description VARCHAR(500),
    created_at DATETIME,
    INDEX idx_user (user_id),
    INDEX idx_created (created_at)
) COMMENT='账单记录';
```

#### 验收标准（AC-4）
- [ ] 生成前显示报价
- [ ] 报价金额 = 结算金额（误差 ≤1%）
- [ ] 余额不足时拦截生成
- [ ] 任务失败时全额退款
- [ ] 账单与流水可对账

### 4.6 M5 部署与优化（Week 19-22）

#### 目标
实现一键部署、在线升级、性能优化。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 运维-Docker 镜像 | 运维 | 多阶段构建镜像，大小 ≤500MB | 3 天 |
| 运维-docker-compose | 运维 | 一键启动所有服务 | 2 天 |
| 运维-安装脚本 | 运维 | aid-install.sh（参考原项目） | 3 天 |
| 后端-性能优化 | 后端 | 任务队列吞吐 ≥10 镜/分钟 | 4 天 |
| 后端-监控埋点 | 后端 | Prometheus + Grafana 监控 | 3 天 |
| 前端-打包优化 | 前端 | Gzip + CDN，首屏 ≤2s | 2 天 |
| 文档-部署文档 | 文档 | 安装/升级/回滚操作手册 | 3 天 |

#### Docker Compose 示例

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: easywing_aid
    volumes:
      - ./sql:/docker-entrypoint-initdb.d
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  easywing-backend:
    image: easywing/aid-backend:${VERSION}
    depends_on:
      - mysql
      - redis
      - minio
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/easywing_aid
      SPRING_REDIS_HOST: redis
      MINIO_ENDPOINT: http://minio:9000
    ports:
      - "8080:8080"
    volumes:
      - ./logs:/app/logs

  easywing-frontend:
    image: easywing/aid-frontend:${VERSION}
    depends_on:
      - easywing-backend
    ports:
      - "80:80"
    environment:
      BACKEND_URL: http://easywing-backend:8080

volumes:
  mysql_data:
  redis_data:
  minio_data:
```

#### 验收标准（AC-6/7）
- [ ] 全新 2 核 4GB 服务器 30 分钟内完成安装
- [ ] 访问 `http://SERVER_IP` 可登录
- [ ] 升级过程中断，系统自动回滚
- [ ] 回滚后服务可用，数据无损

### 4.7 M6 打磨上线（Week 23-26）

#### 目标
质量门、文档、培训，准备上线。

#### 任务清单

| 任务 | 负责人 | 交付物 | 时间 |
|---|---|---|---|
| 测试-全量回归 | QA | 所有 AC 通过报告 | 5 天 |
| 安全-渗透测试 | 安全 | 漏洞修复报告 | 3 天 |
| 文档-用户手册 | 文档 | 用户操作手册（含视频） | 5 天 |
| 文档-开发文档 | 文档 | Provider 接入指南 | 3 天 |
| 培训-内部培训 | PM | 给客服/运营培训 2 场 | 2 天 |
| 上线-灰度发布 | 运维 | 10% 流量灰度 → 100% | 3 天 |

#### 质量门清单

**功能完整性**：
- [ ] AC-1~15 全部通过
- [ ] 所有 P0/P1 bug 已修复

**性能指标**：
- [ ] 进度推送延迟 ≤1 秒（PF-1）
- [ ] SSE 断线恢复 ≤3 秒（PF-2）
- [ ] 批量任务吞吐 ≥10 镜/分钟（PF-3）
- [ ] 页面交互响应 ≤200ms（PF-4）

**安全基线**：
- [ ] 管理后台入口随机化
- [ ] 初始密码强制修改
- [ ] TOKEN_SECRET 非默认值
- [ ] SQL 注入/XSS 防护

**文档完整性**：
- [ ] 用户手册
- [ ] 管理员手册
- [ ] 开发文档（Provider 接入指南）
- [ ] API 文档（Swagger）

---

## 5. 数据模型设计

### 5.1 ER 图

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   Project    │1────n│   Episode    │1────n│     Shot     │
│ 创作项目     │       │   分集       │       │   分镜       │
└──────────────┘       └──────────────┘       └──────────────┘
       │1                                             │
       │                                              │
       │n                                             │1
┌──────────────┐                              ┌──────────────┐
│    Asset     │                              │ GenerationTask│
│   资产库     │                              │  生成任务     │
└──────────────┘                              └──────────────┘
       │1
       │
       │n
┌──────────────┐
│ VisualConfig │
│ 视觉设定快照  │
└──────────────┘
```

### 5.2 核心字段说明

#### aid_project（项目表）
| 字段 | 类型 | 说明 |
|---|---|---|
| visual_config_snapshot | JSON | 视觉设定快照（风格块 + 色盘 + 约束），示例见下 |
| status | VARCHAR | DRAFT / IN_PROGRESS / COMPLETED |

**visual_config_snapshot 示例**：
```json
{
  "styleBlock": "二次元动漫风格，赛璐璐渲染，日系少年漫画画风",
  "colorPalette": "#FF5733,#33FF57,#3357FF",
  "constraints": [
    "禁止真人照片",
    "角色年龄 ≥18 岁",
    "无暴力血腥"
  ],
  "version": "v1.2",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

#### aid_asset（资产表）
| 字段 | 类型 | 说明 |
|---|---|---|
| asset_code | VARCHAR | 资产编号，如 C01（角色）、S01（场景） |
| asset_type | VARCHAR | CHARACTER / SCENE / PROP |
| prompt_template | TEXT | 提示词模板，如 "{{styleBlock}}, 少年剑客, {{colorPalette}}" |
| version | INT | 资产版本号，重命名后递增 |

#### aid_shot（分镜表）
| 字段 | 类型 | 说明 |
|---|---|---|
| prompt_final | TEXT | **最终提示词**（审计用），记录传给 AI 的完整 prompt |
| camera_movement | VARCHAR | 镜头语言，如 "推镜" / "摇镜" / "特写" |

#### aid_generation_task（生成任务表）
| 字段 | 类型 | 说明 |
|---|---|---|
| task_type | VARCHAR | IMAGE / VIDEO / AUDIO |
| provider | VARCHAR | 使用的 Provider，如 tongyi-image |
| status | VARCHAR | PENDING / RUNNING / COMPLETED / FAILED / PAUSED |
| progress | INT | 进度百分比 0-100 |
| retry_count | INT | 已重试次数，≥3 次标记为最终失败 |

---

## 6. 技术风险与对策

### 6.1 高风险项

| 风险 | 影响 | 概率 | 对策 |
|---|---|---|---|
| **R1：AI 服务商 API 变更** | 影响生成链路 | 中 | 1. Provider 层抽象隔离<br>2. 版本化适配器<br>3. 监控异常告警 |
| **R2：FFmpeg 性能瓶颈** | 导出慢，用户体验差 | 高 | 1. 异步任务队列<br>2. 多实例并行<br>3. GPU 加速（可选） |
| **R3：任务队列堆积** | 生成失败/超时 | 中 | 1. 限流+排队提示<br>2. 优先级队列<br>3. 自动扩容 |
| **R4：存储成本过高** | 运营成本高 | 中 | 1. OSS 生命周期策略<br>2. 7 天后转归档<br>3. 用户自带存储 |
| **R5：视觉一致性差** | 用户不满意 | 高 | 1. LoRA 微调（后期）<br>2. 参考图引导<br>3. 多轮迭代 |

### 6.2 技术债务

| 债务 | 影响 | 偿还计划 |
|---|---|---|
| Provider 层未做熔断降级 | 单点故障影响全局 | M5 补充 Hystrix |
| 任务幂等性依赖分布式锁 | Redis 故障影响生成 | M6 改为数据库唯一约束 |
| 前端用户端与管理端代码重复 | 维护成本高 | M6 抽取公共组件库 |

---

## 7. 资源与时间估算

### 7.1 人力需求

| 角色 | 人数 | 时间 | 说明 |
|---|---|---|---|
| 后端开发（Java） | 2 人 | 6 个月 | 核心业务逻辑 + Provider 层 |
| 前端开发（Vue） | 2 人 | 6 个月 | 用户端 + 管理端 |
| 算法工程师 | 1 人 | 3 个月 | 提示词优化 + 视觉一致性调优 |
| 测试工程师 | 1 人 | 4 个月 | 功能测试 + 性能测试 |
| 运维工程师 | 1 人 | 2 个月 | 部署脚本 + 监控 |
| 产品经理 | 1 人 | 6 个月 | 需求细化 + 验收 |

**总计**：8 人 × 平均 4.5 个月 = **36 人月**

### 7.2 成本估算（按 6 个月）

| 项目 | 成本（元） | 说明 |
|---|---|---|
| 人力成本 | 36 万 | 36 人月 × 1 万/月 |
| 云服务器（开发/测试） | 2 万 | 4 核 8GB × 2 台 × 6 个月 |
| AI 服务费用 | 5 万 | 测试调用费用（1000 次生成） |
| OSS 存储 | 1 万 | 1TB × 6 个月 |
| 其他（域名/CDN） | 0.5 万 | - |
| **总计** | **44.5 万** | - |

### 7.3 关键路径

```
M0 技术预研 (2w) 
    → M1 骨架搭建 (4w)
        → M2 分镜闭环 (4w)
            → M3 音画成片 (4w)
                → M4 商业化 (4w)
                    → M5 部署优化 (4w)
                        → M6 打磨上线 (4w)
```

**关键路径总长**：26 周（约 6.5 个月）

**缓冲时间**：预留 2 周（总计 28 周）

---

## 8. 验收与交付

### 8.1 验收标准矩阵

| AC 编号 | 验收项 | 负责人 | 验收方式 | 通过标准 |
|---|---|---|---|---|
| AC-1 | 端到端跑通 | QA | 手工测试 | ≥60 秒成片导出成功 |
| AC-2 | 角色一致性 | 算法 | 人工评审 | ≥80% 一致性 |
| AC-3 | 任务治理 | 后端 | 自动化测试 | 重启后任务不重复/悬挂 |
| AC-4 | 计费一致性 | 后端 | 自动化测试 | 报价=结算（误差 ≤1%） |
| AC-5 | Provider 扩展 | 后端 | 代码审查 | 新增 Provider 无需改业务代码 |
| AC-6 | 一键部署 | 运维 | 实机测试 | 30 分钟内完成安装 |
| AC-7 | 升级回滚 | 运维 | 实机测试 | 中断后自动回滚 |
| AC-8 | 安全基线 | 安全 | 渗透测试 | 无高危/中危漏洞 |
| AC-11 | 视觉设定可导出 | 前端 | 手工测试 | 导出/导入配置生效 |
| AC-12 | 资产追溯 | 后端 | 自动化测试 | 重命名后引用不失效 |

### 8.2 交付清单

#### 代码交付物
- [ ] `easywing-platform-aid` 模块源码
- [ ] 前端用户端代码（Vue 3）
- [ ] 前端管理端代码（已集成）
- [ ] 单元测试（覆盖率 ≥60%）
- [ ] 集成测试脚本

#### 部署交付物
- [ ] Docker 镜像（后端 + 前端）
- [ ] docker-compose.yml
- [ ] 安装脚本 aid-install.sh
- [ ] SQL 初始化脚本

#### 文档交付物
- [ ] 用户操作手册（含视频）
- [ ] 管理员手册
- [ ] API 文档（Swagger）
- [ ] Provider 接入指南
- [ ] 运维手册（部署/升级/回滚）
- [ ] 架构设计文档

#### 配置交付物
- [ ] Prometheus 监控配置
- [ ] Grafana 仪表盘
- [ ] Nginx 反向代理配置

### 8.3 上线检查清单

**上线前（T-7 天）**：
- [ ] 所有 AC 通过
- [ ] 性能测试达标（PF-1~4）
- [ ] 安全扫描通过
- [ ] 灰度环境部署成功

**上线当天（T-Day）**：
- [ ] 数据库备份
- [ ] 10% 流量灰度 2 小时
- [ ] 监控无异常告警
- [ ] 100% 切流

**上线后（T+7 天）**：
- [ ] 每日巡检监控
- [ ] 收集用户反馈
- [ ] 紧急 bug 修复

---

## 9. 附录

### 9.1 技术选型对比

#### 任务队列

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **Redis Stream** | 轻量、部署简单、已有 Redis | 功能较弱、无事务 | ✅ **推荐**（MVP 阶段） |
| RocketMQ | 功能强大、事务消息 | 重量级、需额外部署 | 备选（后期考虑） |
| RabbitMQ | 生态成熟 | 性能较弱 | 不推荐 |

#### 前端用户端

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **Vue 3 SPA** | 技术栈统一、开发效率高 | 无 SSR，SEO 较弱 | ✅ **推荐**（内部工具优先） |
| Next.js | SEO 友好、官方方案 | 多技术栈维护成本 | 备选（C 端产品时考虑） |

### 9.2 核心依赖版本

| 依赖 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.2.x | 已有基础 |
| Spring Cloud | 2023.0.x | 网关/配置中心 |
| Vue | 3.4.x | 前端框架 |
| Element Plus | 2.5.x | UI 组件库 |
| FFmpeg | 6.0+ | 视频处理 |
| Redis | 7.0+ | 缓存+队列 |
| MySQL | 8.0+ | 主数据库 |
| MinIO | RELEASE.2024-01-xx | 对象存储 |

### 9.3 参考资料

| 文档 | 链接 |
|---|---|
| AID Studio 官方仓库 | https://github.com/shijue-aid/aid-studio |
| EasyWingPlatform 架构文档 | `docs/ARCHITECTURE.md` |
| FFmpeg 官方文档 | https://ffmpeg.org/documentation.html |
| Spring Cloud Gateway | https://docs.spring.io/spring-cloud-gateway/ |
| Redis Streams | https://redis.io/docs/data-types/streams/ |

### 9.4 变更历史

| 版本 | 日期 | 变更内容 | 负责人 |
|---|---|---|---|
| v1.0 | 2025-01-XX | 初始版本，6 个月实施计划 | - |

---

**文档结束**
