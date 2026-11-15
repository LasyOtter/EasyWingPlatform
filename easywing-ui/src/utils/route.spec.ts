import { describe, expect, it } from 'vitest'
import {
  formatComponentPath,
  formatPath,
  handleRedirect,
  isExternalLink,
  isValidRoutePath
} from './route'

describe('route utils', () => {
  it('formatPath 补全前导斜杠', () => {
    expect(formatPath('system/user')).toBe('/system/user')
    expect(formatPath('/system/user')).toBe('/system/user')
    expect(formatPath('')).toBe('')
  })

  it('formatComponentPath 去掉前导斜杠', () => {
    expect(formatComponentPath('/views/system/user/index.vue')).toBe('views/system/user/index.vue')
    expect(formatComponentPath('views/index.vue')).toBe('views/index.vue')
    expect(formatComponentPath('')).toBe('')
  })

  it('handleRedirect 处理空路径与重定向页', () => {
    expect(handleRedirect('')).toBe('/')
    expect(handleRedirect('/redirect')).toBe('/')
    expect(handleRedirect('/system/user')).toBe('/system/user')
  })

  it('isExternalLink 识别外部链接', () => {
    expect(isExternalLink('https://example.com')).toBe(true)
    expect(isExternalLink('mailto:a@b.com')).toBe(true)
    expect(isExternalLink('tel:123456')).toBe(true)
    expect(isExternalLink('/system/user')).toBe(false)
  })

  it('isValidRoutePath 要求以斜杠开头', () => {
    expect(isValidRoutePath('/system/user')).toBe(true)
    expect(isValidRoutePath('system/user')).toBe(false)
  })
})
