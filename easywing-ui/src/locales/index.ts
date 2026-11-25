import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'

export const SUPPORT_LOCALES = ['zh-CN'] as const
export type AppLocale = (typeof SUPPORT_LOCALES)[number]

const STORAGE_KEY = 'app-locale'

/** 读取持久化的语言偏好，缺省回退到浏览器语言，再回退到简体中文。 */
function resolveInitialLocale(): AppLocale {
  const cached = localStorage.getItem(STORAGE_KEY)
  if (cached && isSupported(cached)) {
    return cached as AppLocale
  }

  const browser = navigator.language?.replace('_', '-') ?? ''
  if (isSupported(browser)) {
    return browser as AppLocale
  }

  // 繁体/英文等尚未提供语言包时保持简体，避免出现空白文案
  return 'zh-CN'
}

function isSupported(locale: string): boolean {
  return (SUPPORT_LOCALES as readonly string[]).includes(locale)
}

const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN
  }
})

/**
 * 切换语言并持久化。Element Plus 的语言包在 main.ts 中随之外层同步。
 */
export function setLocale(locale: AppLocale): void {
  if (!isSupported(locale) || i18n.global.locale.value === locale) {
    return
  }
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

export function getCurrentLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale
}

export default i18n
