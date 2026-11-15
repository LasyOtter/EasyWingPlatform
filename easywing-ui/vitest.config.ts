import { defineConfig } from 'vitest/config'

// 单元测试不需要 Vite 业务插件（mock / svg-icons / 自动导入），
// 独立配置可避免插件启动的服务阻止测试进程退出。
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts']
  }
})
