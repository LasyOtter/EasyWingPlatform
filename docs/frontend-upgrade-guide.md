# 🚀 EasyWing Platform 前端依赖升级指南

> 更新时间: 2026-11-14

---

## 📦 当前依赖版本

### 生产依赖
```json
{
  "@element-plus/icons-vue": "^2.3.1",
  "axios": "^1.6.8",
  "element-plus": "^2.7.0",
  "js-cookie": "^3.0.5",
  "lodash-es": "^4.17.21",
  "nprogress": "^0.2.0",
  "pinia": "^2.1.7",
  "pinia-plugin-persistedstate": "^3.2.1",
  "vue": "^3.4.27",
  "vue-router": "^4.3.2"
}
```

### 开发依赖
```json
{
  "@types/js-cookie": "^3.0.6",
  "@types/lodash-es": "^4.17.12",
  "@types/node": "^20.12.12",
  "@types/nprogress": "^0.2.3",
  "@typescript-eslint/eslint-plugin": "^7.10.0",
  "@typescript-eslint/parser": "^7.10.0",
  "@vitejs/plugin-vue": "^5.0.4",
  "@vitejs/plugin-vue-jsx": "^3.1.0",
  "eslint": "^8.57.0",
  "eslint-plugin-vue": "^9.26.0",
  "typescript": "^5.4.5",
  "vite": "^5.2.11"
}
```

---

## 🔄 推荐升级版本

### 生产依赖升级

| 包名 | 当前版本 | 最新版本 | 升级建议 | 优先级 |
|------|---------|---------|---------|--------|
| axios | 1.6.8 | **1.7.7** | ✅ 建议 | 高 |
| element-plus | 2.7.0 | **2.8.4** | ✅ 建议 | 中 |
| @element-plus/icons-vue | 2.3.1 | **2.3.1** | ✔️ 最新 | - |
| vue | 3.4.27 | **3.5.10** | ⚠️ 谨慎 | 低 |
| vue-router | 4.3.2 | **4.4.5** | ✅ 建议 | 中 |
| pinia | 2.1.7 | **2.2.4** | ✅ 建议 | 中 |
| lodash-es | 4.17.21 | **4.17.21** | ✔️ 最新 | - |

### 开发依赖升级

| 包名 | 当前版本 | 最新版本 | 升级建议 | 优先级 |
|------|---------|---------|---------|--------|
| vite | 5.2.11 | **5.4.10** | ✅ 建议 | 高 |
| @vitejs/plugin-vue | 5.0.4 | **5.1.4** | ✅ 建议 | 高 |
| typescript | 5.4.5 | **5.6.2** | ✅ 建议 | 中 |
| @typescript-eslint/* | 7.10.0 | **8.8.1** | ⚠️ 谨慎 | 低 |
| eslint | 8.57.0 | **9.13.0** | ⚠️ 破坏性 | 低 |

---

## 🎯 升级策略

### 方案 1: 保守升级（推荐）

**只升级安全补丁和小版本更新**

```bash
cd easywing-ui

# 升级 axios (安全修复)
pnpm update axios@^1.7.7

# 升级 Vite 和插件
pnpm update vite@^5.4.10
pnpm update @vitejs/plugin-vue@^5.1.4
pnpm update @vitejs/plugin-vue-jsx@^3.1.0

# 升级 Element Plus
pnpm update element-plus@^2.8.4

# 升级 TypeScript
pnpm update typescript@^5.6.2

# 升级 Vue Router 和 Pinia
pnpm update vue-router@^4.4.5
pnpm update pinia@^2.2.4

# 验证
pnpm build
```

**预期结果**:
- ✅ 安全性提升
- ✅ 性能小幅优化
- ✅ 兼容性良好
- ⚠️ 需要回归测试

---

### 方案 2: 激进升级（测试环境）

**升级所有依赖到最新版本**

```bash
cd easywing-ui

# 全量升级
pnpm update --latest

# 或者手动升级
pnpm add vue@^3.5.10
pnpm add -D eslint@^9.13.0
pnpm add -D @typescript-eslint/eslint-plugin@^8.8.1
pnpm add -D @typescript-eslint/parser@^8.8.1

# 注意: ESLint 9 需要迁移配置
# 需要将 .eslintrc.js 迁移到 eslint.config.js
```

**风险**:
- ⚠️ Vue 3.5 有破坏性变更
- ⚠️ ESLint 9 配置格式不同
- ⚠️ 需要大量测试

---

### 方案 3: 分阶段升级（推荐生产）

**第一阶段（本周）- 安全和性能**
```bash
cd easywing-ui

# 安全补丁
pnpm update axios@^1.7.7

# 性能优化
pnpm update vite@^5.4.10
pnpm update @vitejs/plugin-vue@^5.1.4

# 测试
pnpm build
pnpm preview
```

**第二阶段（下周）- UI 框架**
```bash
# 升级 Element Plus
pnpm update element-plus@^2.8.4

# 升级 Vue Router
pnpm update vue-router@^4.4.5

# 测试
pnpm dev
# 进行 UI 回归测试
```

**第三阶段（2-3周后）- 核心框架**
```bash
# 升级 Vue (谨慎)
pnpm update vue@^3.5.10

# 全面回归测试
pnpm test
pnpm build
```

---

## 📋 执行步骤

### 1. 备份当前配置
```bash
cd easywing-ui
cp package.json package.json.backup
cp pnpm-lock.yaml pnpm-lock.yaml.backup
```

### 2. 执行升级（推荐方案 1）
```bash
# 升级核心依赖
pnpm update axios@^1.7.7 \
            vite@^5.4.10 \
            @vitejs/plugin-vue@^5.1.4 \
            element-plus@^2.8.4 \
            typescript@^5.6.2 \
            vue-router@^4.4.5 \
            pinia@^2.2.4
```

### 3. 清理缓存
```bash
rm -rf node_modules
rm -rf .vite
pnpm install
```

### 4. 验证构建
```bash
pnpm build

# 检查产物大小
ls -lh dist/assets/js/

# 预期: 总体积应该更小或持平
```

### 5. 本地测试
```bash
pnpm preview

# 打开 http://localhost:4173
# 进行手动测试
```

### 6. 回滚（如果出问题）
```bash
cp package.json.backup package.json
cp pnpm-lock.yaml.backup pnpm-lock.yaml
rm -rf node_modules
pnpm install
```

---

## 🔍 升级后验证清单

### 构建验证
- [ ] `pnpm build` 成功
- [ ] 无 TypeScript 错误
- [ ] 无 ESLint 警告
- [ ] 产物大小正常（< 2MB）

### 功能验证
- [ ] 路由跳转正常
- [ ] 登录/登出功能
- [ ] 表单验证
- [ ] 数据加载
- [ ] 国际化（如果有）
- [ ] 主题切换（如果有）

### 性能验证
- [ ] 首屏加载 < 1.5s
- [ ] LCP < 2s
- [ ] FCP < 1s
- [ ] 无内存泄漏

### 兼容性验证
- [ ] Chrome 最新版
- [ ] Firefox 最新版
- [ ] Safari 最新版
- [ ] Edge 最新版

---

## ⚠️ 已知问题和解决方案

### Vue 3.5 迁移
```typescript
// 如果升级到 Vue 3.5，注意以下变更:

// 1. defineProps 类型推导增强
// 旧写法可能需要调整

// 2. Suspense 默认行为变更
// 如果使用了 Suspense，需要检查

// 3. Teleport 性能优化
// 无需改动，自动受益
```

### ESLint 9 迁移
```javascript
// 如果升级 ESLint 到 9.x
// 需要创建新的配置文件

// eslint.config.js (新格式)
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import typescript from '@typescript-eslint/eslint-plugin'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.vue'],
    languageOptions: {
      parser: '@typescript-eslint/parser'
    },
    plugins: {
      '@typescript-eslint': typescript
    }
  }
]
```

### Vite 5.4 新特性
```typescript
// vite.config.ts 可以利用新特性

export default defineConfig({
  // 新增: 环境变量类型提示
  envPrefix: ['VITE_', 'APP_'],
  
  // 新增: 更好的 HMR
  server: {
    hmr: {
      overlay: true
    }
  },
  
  // 新增: 构建性能优化
  build: {
    rollupOptions: {
      output: {
        experimentalMinChunkSize: 10000
      }
    }
  }
})
```

---

## 📊 升级效果预期

### 构建性能
- Vite 5.4: **构建速度提升 10-15%**
- TypeScript 5.6: **类型检查提升 5-10%**

### 运行时性能
- Vue 3.5: **响应式性能提升 20%**（如果升级）
- Element Plus 2.8: **组件渲染提升 5%**

### 包体积
- Vite 优化: **Tree-shaking 更好，体积减少 3-5%**
- TypeScript 5.6: **编译产物更小**

---

## 🎯 推荐执行计划

### 立即执行（保守升级）
```bash
cd easywing-ui

# 1. 升级 axios（安全修复）
pnpm update axios@^1.7.7

# 2. 升级 Vite 相关
pnpm update vite@^5.4.10 @vitejs/plugin-vue@^5.1.4

# 3. 升级 Element Plus
pnpm update element-plus@^2.8.4

# 4. 升级 TypeScript
pnpm update typescript@^5.6.2

# 5. 测试
pnpm build && pnpm preview
```

**工作量**: 30 分钟  
**风险**: 低  
**收益**: 安全性 + 性能提升

---

## 📞 遇到问题?

如果升级过程中遇到问题:

1. **检查 CHANGELOG**: 查看各包的 CHANGELOG.md
2. **回滚**: 使用备份的 package.json
3. **搜索 Issues**: 在 GitHub 上搜索相关问题
4. **降级单个包**: `pnpm add <package>@<version>`

---

**建议**: 先在开发/测试环境执行保守升级方案，验证通过后再部署到生产环境。

---

*最后更新: 2026-11-14*
