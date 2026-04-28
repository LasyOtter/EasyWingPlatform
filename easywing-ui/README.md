# EasyWing UI

企业级 Vue 3.0 后台管理系统前端

## 特性

- 基于 Vue 3.0 + TypeScript + Vite
- 使用 Element Plus UI 组件库
- 采用 Pinia 状态管理
- 集成权限控制系统
- 支持动态路由
- 完善的 API 封装
- RESTful API 设计

## 技术栈

| 技术 | 说明 |
|------|------|
| Vue 3.0 | 渐进式 JavaScript 框架 |
| TypeScript | JavaScript 超集 |
| Vite | 新一代前端构建工具 |
| Element Plus | Vue 3.0 UI 组件库 |
| Pinia | Vue 状态管理 |
| Vue Router | Vue 官方路由 |
| Axios | HTTP 请求库 |
| SCSS | CSS 预处理器 |

## 项目结构

```
easywing-ui/
├── public/              # 静态资源
├── src/
│   ├── api/             # API 接口封装
│   │   ├── core/        # 核心请求封装
│   │   └── system/     # 系统模块接口
│   ├── assets/         # 资源文件
│   │   ├── icons/      # SVG 图标
│   │   ├── images/     # 图片资源
│   │   └── styles/     # 样式文件
│   ├── components/      # 组件
│   │   ├── common/     # 公共组件
│   │   └── layout/     # 布局组件
│   ├── directives/      # 指令
│   ├── hooks/           # Hooks
│   ├── router/          # 路由配置
│   ├── store/           # 状态管理
│   ├── types/           # 类型定义
│   ├── utils/           # 工具函数
│   ├── views/           # 页面视图
│   │   ├── dashboard/   # 仪表盘
│   │   ├── error/       # 错误页面
│   │   ├── login/       # 登录页
│   │   ├── profile/     # 个人中心
│   │   └── system/      # 系统管理
│   ├── App.vue          # 根组件
│   └── main.ts          # 入口文件
├── .env.development    # 开发环境配置
├── .env.production     # 生产环境配置
├── index.html          # HTML 入口
├── package.json        # 包管理
├── tsconfig.json       # TypeScript 配置
└── vite.config.ts     # Vite 配置
```

## 快速开始

### 环境要求

- Node.js >= 18.0.0
- pnpm >= 8.0.0

### 安装依赖

```bash
pnpm install
```

### 开发模式

```bash
pnpm dev
```

### 构建生产版本

```bash
pnpm build
```

### 预览生产版本

```bash
pnpm preview
```

## 接口配置

项目默认对接 `http://localhost:8080` 后端服务。如需修改，请编辑 `.env.development` 文件：

```env
VITE_APP_BASE_API=/api
```

## 功能模块

### 已完成

- [x] 登录/退出
- [x] 动态路由
- [x] 权限指令
- [x] 用户管理
- [x] 角色管理
- [x] 菜单管理
- [x] 部门管理
- [x] 字典管理
- [x] 个人中心

### 待开发

- [ ] 操作日志
- [ ] 登录日志
- [ ] 通知公告
- [ ] 代码生成
- [ ] 系统配置

## 许可证

MIT License
