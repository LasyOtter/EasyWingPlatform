import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import type { Language } from 'element-plus/es/locale'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import enUs from 'element-plus/dist/locale/en.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'normalize.css/normalize.css'
import 'nprogress/nprogress.css'

import App from './App.vue'
import router from './router'
import i18n, { getCurrentLocale } from './locales'
import { setupPermissions } from './directives/permission'

import { setToken } from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'
import { AxiosCanceler } from '@/api/core/axiosCancel'
import '@/assets/styles/index.scss'

/** 应用语言与 Element Plus 内置文案包保持一致。 */
const elementPlusLocales: Record<string, Language> = {
  'zh-CN': zhCn,
  'en-US': enUs
}

// 创建应用实例
const app = createApp(App)

// 创建 Pinia 实例
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

// 注册全局 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 创建 axios 取消器实例
const axiosCanceler = new AxiosCanceler()

// HTTP 响应拦截器 - 处理 Token 过期
app.config.errorHandler = (err, _instance, info) => {
  console.error('Global error:', err)
  console.error('Error info:', info)
}

// 全局属性配置
app.config.globalProperties.$httpCanceler = axiosCanceler

// 使用插件
app.use(pinia)
app.use(router)
app.use(i18n)
// 刷新后从持久化状态恢复令牌，保证请求拦截器能读取到
const userStore = useUserStore()
if (userStore.token) {
  setToken(userStore.token)
}
app.use(ElementPlus, {
  locale: elementPlusLocales[getCurrentLocale()] ?? zhCn,
  size: 'default'
})

// 设置权限指令
setupPermissions(app)

document.documentElement.lang = getCurrentLocale()

// 全局错误处理
app.mount('#app')
