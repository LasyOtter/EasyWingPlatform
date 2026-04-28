import { createPinia } from 'pinia'
import type { App } from 'vue'

const store = createPinia()

export function setupStore(app: App) {
  app.use(store)
}

export { store }

export * from './modules/user'
export * from './modules/permission'
