import Cookies from 'js-cookie'

const TOKEN_KEY = 'X-Access-Token'

/**
 * 获取 Token
 */
export function getToken(): string | undefined {
  return Cookies.get(TOKEN_KEY)
}

/**
 * 设置 Token
 */
export function setToken(token: string, expires?: number): void {
  if (expires) {
    Cookies.set(TOKEN_KEY, token, { expires: expires / (24 * 60 * 60) })
  } else {
    Cookies.set(TOKEN_KEY, token)
  }
}

/**
 * 移除 Token
 */
export function removeToken(): void {
  Cookies.remove(TOKEN_KEY)
}

/**
 * 清除所有缓存
 */
export function clearAll(): void {
  Cookies.remove(TOKEN_KEY)
  localStorage.clear()
  sessionStorage.clear()
}
