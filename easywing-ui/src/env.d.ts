/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_BASE_API: string
  readonly VITE_APP_PORT: string
  readonly VITE_APP_USE_MOCK: string
  readonly VITE_APP_PERMISSION: string
  readonly VITE_APP_TOKEN_NAME: string
  readonly VITE_APP_ROUTER_BASE: string
  readonly VITE_APP_SOURCEMAP: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
