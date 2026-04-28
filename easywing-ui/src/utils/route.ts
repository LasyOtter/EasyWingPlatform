import type { RouteRecordRaw } from 'vue-router'

/**
 * 格式化路由路径
 */
export function formatPath(path: string): string {
  if (!path) return ''
  if (path.startsWith('/')) {
    return path
  }
  return `/${path}`
}

/**
 * 格式化路由组件路径
 */
export function formatComponentPath(path: string): string {
  if (!path) return ''
  if (path.startsWith('/')) {
    return path.slice(1)
  }
  return path
}

/**
 * 路由重定向处理
 */
export function handleRedirect(path: string): string {
  if (!path) return '/'
  if (path === '/redirect') return '/'
  return path
}

/**
 * 验证 URL 链接
 */
export function isExternalLink(path: string): boolean {
  return /^(https?:|mailto:|tel:)/.test(path)
}

/**
 * 验证路由路径
 */
export function isValidRoutePath(path: string): boolean {
  return /^\//.test(path)
}
