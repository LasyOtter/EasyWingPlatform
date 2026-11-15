// 错误追踪集成 - Sentry (可选)
// 安装: pnpm add @sentry/vue

import * as Sentry from '@sentry/vue'
import type { App } from 'vue'
import type { Router } from 'vue-router'

/**
 * 错误追踪配置
 */
const ERROR_TRACKING_CONFIG = {
  // 是否启用错误追踪
  enabled: import.meta.env.VITE_ERROR_TRACKING_ENABLED === 'true',
  // Sentry DSN
  dsn: import.meta.env.VITE_SENTRY_DSN,
  // 环境
  environment: import.meta.env.MODE,
  // 版本
  release: import.meta.env.VITE_APP_VERSION || '1.0.0',
  // 采样率
  tracesSampleRate: parseFloat(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE || '0.1'),
  // 会话重放采样率
  replaysSessionSampleRate: parseFloat(import.meta.env.VITE_SENTRY_REPLAY_SAMPLE_RATE || '0.1'),
  // 错误会话重放采样率
  replaysOnErrorSampleRate: parseFloat(import.meta.env.VITE_SENTRY_REPLAY_ERROR_RATE || '1.0')
}

/**
 * 自定义错误类型
 */
export enum ErrorType {
  API = 'API',
  RUNTIME = 'RUNTIME',
  RESOURCE = 'RESOURCE',
  PROMISE = 'PROMISE',
  VUE = 'VUE'
}

/**
 * 错误信息接口
 */
interface ErrorInfo {
  type: ErrorType
  message: string
  stack?: string
  url?: string
  line?: number
  column?: number
  timestamp: number
  userAgent: string
  userId?: string
  extra?: Record<string, any>
}

/**
 * 错误缓冲区
 */
let errorBuffer: ErrorInfo[] = []
const MAX_BUFFER_SIZE = 50
const REPORT_INTERVAL = 10000

/**
 * 上报错误到服务器
 */
async function reportErrors(errors: ErrorInfo[]) {
  if (errors.length === 0) return

  try {
    await fetch('/api/monitor/errors', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        errors,
        timestamp: Date.now()
      }),
      keepalive: true
    })
  } catch (error) {
    console.error('Failed to report errors:', error)
  }
}

/**
 * 添加错误到缓冲区
 */
function bufferError(errorInfo: ErrorInfo) {
  errorBuffer.push(errorInfo)

  // 开发环境直接输出
  if (import.meta.env.DEV) {
    console.error('[Error Tracking]', errorInfo)
  }

  // 达到最大缓冲大小，立即上报
  if (errorBuffer.length >= MAX_BUFFER_SIZE) {
    reportErrors([...errorBuffer])
    errorBuffer = []
  }
}

/**
 * 定时上报错误
 */
setInterval(() => {
  if (errorBuffer.length > 0) {
    reportErrors([...errorBuffer])
    errorBuffer = []
  }
}, REPORT_INTERVAL)

/**
 * 初始化 Sentry 错误追踪
 */
export function initSentryTracking(app: App, router: Router) {
  if (!ERROR_TRACKING_CONFIG.enabled || !ERROR_TRACKING_CONFIG.dsn) {
    console.log('[Sentry] Error tracking is disabled or DSN is not configured')
    return
  }

  Sentry.init({
    app,
    dsn: ERROR_TRACKING_CONFIG.dsn,
    environment: ERROR_TRACKING_CONFIG.environment,
    release: ERROR_TRACKING_CONFIG.release,
    tracePropagationTargets: ['localhost', /^\//],

    // 性能监控
    integrations: [
      Sentry.browserTracingIntegration({ router }),
      Sentry.replayIntegration({
        maskAllText: true,
        blockAllMedia: true
      })
    ],

    // 采样率
    tracesSampleRate: ERROR_TRACKING_CONFIG.tracesSampleRate,

    // 会话重放
    replaysSessionSampleRate: ERROR_TRACKING_CONFIG.replaysSessionSampleRate,
    replaysOnErrorSampleRate: ERROR_TRACKING_CONFIG.replaysOnErrorSampleRate,

    // 忽略的错误
    ignoreErrors: [
      'ResizeObserver loop limit exceeded',
      'Non-Error promise rejection captured',
      'Network Error',
      'Request aborted',
      'Request failed with status code'
    ],

    // 面包屑
    beforeBreadcrumb(breadcrumb) {
      // 过滤敏感信息
      if (breadcrumb.category === 'xhr' || breadcrumb.category === 'fetch') {
        if (breadcrumb.data?.url?.includes('password')) {
          return null
        }
      }
      return breadcrumb
    },

    // 事件处理
    beforeSend(event) {
      // 开发环境不发送
      if (import.meta.env.DEV) {
        console.log('[Sentry] Event would be sent:', event)
        return null
      }
      return event
    }
  })

  console.log('[Sentry] Error tracking initialized')
}

/**
 * 初始化原生错误追踪（不使用 Sentry）
 */
export function initNativeErrorTracking() {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    console.log('[Error Tracking] Using Sentry, native tracking skipped')
    return
  }

  console.log('[Error Tracking] Native error tracking initialized')

  // 全局错误处理
  window.addEventListener('error', (event) => {
    const errorInfo: ErrorInfo = {
      type: ErrorType.RUNTIME,
      message: event.message,
      stack: event.error?.stack,
      url: event.filename,
      line: event.lineno,
      column: event.colno,
      timestamp: Date.now(),
      userAgent: navigator.userAgent
    }
    bufferError(errorInfo)
  })

  // Promise 未捕获错误
  window.addEventListener('unhandledrejection', (event) => {
    const errorInfo: ErrorInfo = {
      type: ErrorType.PROMISE,
      message: event.reason?.message || String(event.reason),
      stack: event.reason?.stack,
      timestamp: Date.now(),
      userAgent: navigator.userAgent
    }
    bufferError(errorInfo)
  })

  // 资源加载错误
  window.addEventListener(
    'error',
    (event) => {
      if (event.target !== window) {
        const target = event.target as HTMLElement
        const errorInfo: ErrorInfo = {
          type: ErrorType.RESOURCE,
          message: `Failed to load resource: ${target.tagName}`,
          url: (target as any).src || (target as any).href,
          timestamp: Date.now(),
          userAgent: navigator.userAgent
        }
        bufferError(errorInfo)
      }
    },
    true
  )

  // 页面卸载时上报剩余错误
  window.addEventListener('beforeunload', () => {
    if (errorBuffer.length > 0) {
      reportErrors([...errorBuffer])
      errorBuffer = []
    }
  })
}

/**
 * 手动上报错误
 */
export function captureError(error: Error, context?: Record<string, any>) {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    Sentry.captureException(error, {
      contexts: context
    })
  } else {
    const errorInfo: ErrorInfo = {
      type: ErrorType.RUNTIME,
      message: error.message,
      stack: error.stack,
      timestamp: Date.now(),
      userAgent: navigator.userAgent,
      extra: context
    }
    bufferError(errorInfo)
  }
}

/**
 * 手动上报消息
 */
export function captureMessage(message: string, level: 'info' | 'warning' | 'error' = 'info') {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    Sentry.captureMessage(message, level)
  } else if (import.meta.env.DEV) {
    console.log(`[Error Tracking] Message (${level}):`, message)
  }
}

/**
 * 设置用户上下文
 */
export function setUser(user: { id: string; username?: string; email?: string }) {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    Sentry.setUser(user)
  }
}

/**
 * 清除用户上下文
 */
export function clearUser() {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    Sentry.setUser(null)
  }
}

/**
 * 添加面包屑
 */
export function addBreadcrumb(message: string, category: string, data?: Record<string, any>) {
  if (ERROR_TRACKING_CONFIG.enabled && ERROR_TRACKING_CONFIG.dsn) {
    Sentry.addBreadcrumb({
      message,
      category,
      data,
      level: 'info'
    })
  }
}
