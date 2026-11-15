const TOKEN_KEY = import.meta.env.VITE_APP_TOKEN_NAME || 'X-Access-Token'

/**
 * 获取 Token
 */
export function getToken(): string | undefined {
  return localStorage.getItem(TOKEN_KEY) || undefined
}

/**
 * 设置 Token
 * <p>
 * 统一使用 localStorage：Pinia 持久化（store/modules/user）也写入 localStorage，
 * 避免 cookie 与 localStorage 双写导致请求携带不到令牌。
 */
export function setToken(token: string, _expires?: number): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 移除 Token
 */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 清除所有缓存
 */
export function clearAll(): void {
  localStorage.clear()
  sessionStorage.clear()
}
