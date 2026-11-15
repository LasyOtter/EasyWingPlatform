import type { App } from 'vue'

// 全局组件列表
const globalComponents: Record<string, any> = {
  // 可以在此添加全局组件
}

// 注册全局组件
export function setupGlobComponents(app: App) {
  Object.keys(globalComponents).forEach((key) => {
    app.component(key, globalComponents[key])
  })
}

export default {
  install(app: App) {
    setupGlobComponents(app)
  }
}
