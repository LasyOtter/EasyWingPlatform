# EasyWing Platform 前端依赖升级指南

> 更新时间: 2026-11-15

## 当前依赖版本

以下版本与 `easywing-ui/package.json` 保持一致。

### 生产依赖

| 包名 | 当前版本 |
------|----------|
| `@element-plus/icons-vue` | `^2.3.2` |
| `@sentry/vue` | `^8.0.0` |
| `axios` | `^1.8.0` |
| `element-plus` | `^2.9.0` |
| `js-cookie` | `^3.0.5` |
| `lodash-es` | `^4.17.21` |
| `normalize.css` | `^8.0.1` |
| `nprogress` | `^0.2.0` |
| `pinia` | `^2.3.0` |
| `pinia-plugin-persistedstate` | `^3.2.3` |
| `vue` | `^3.5.0` |
| `vue-router` | `^4.5.0` |
| `web-vitals` | `^3.0.0` |

### 开发依赖

| 包名 | 当前版本 |
------|----------|
| `@types/node` | `^20.14.0` |
| `@typescript-eslint/eslint-plugin` | `^7.18.0` |
| `@typescript-eslint/parser` | `^7.18.0` |
| `@vitejs/plugin-vue` | `^5.2.1` |
| `@vitejs/plugin-vue-jsx` | `^3.1.0` |
| `eslint` | `^8.57.0` |
| `eslint-plugin-vue` | `^9.33.0` |
| `prettier` | `^3.4.2` |
| `sass` | `^1.83.0` |
| `terser` | `^5.37.0` |
| `typescript` | `~5.6.3` |
| `unplugin-auto-import` | `^0.18.6` |
| `unplugin-vue-components` | `^0.27.5` |
| `vite` | `^5.4.11` |
| `vite-plugin-mock` | `^3.0.2` |
| `vite-plugin-svg-icons` | `^2.0.1` |
| `vue-tsc` | `~2.1.10` |
| `vitest` | `^2.1.9` |

## 环境要求

- Node.js `>=18`
- pnpm `>=8`（本地验证使用 pnpm 8.15.9）

## 验证命令

```bash
cd easywing-ui
pnpm install
pnpm lint
pnpm type-check
pnpm test
pnpm build
```

## 升级注意事项

- Vue 3.5 启用生产环境告警与响应式行为调整，升级后需回归核心页面。
- Element Plus 2.9 需要同步检查主题变量与组件 API 兼容性。
- Vite 5.4 支持当前 `vite.config.ts` 中的手动分包配置。
- TypeScript 保持 `~5.6.3`，暂不跟随 5.7+ 的破坏性变更。
- ESLint 继续使用 8.x 配置；迁移到 Flat Config 前不要直接升级 ESLint 9。

## 回滚方式

升级前提交 `package.json` 与 `pnpm-lock.yaml`，出现问题时恢复这两个文件并重新执行 `pnpm install`。

---

*最后更新: 2026-11-15*
