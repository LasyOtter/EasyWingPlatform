import { describe, expect, it } from 'vitest'
import {
  ALLOWED_IMAGE_EXTENSIONS,
  MAX_IMAGE_SIZE_MB,
  fileExtension,
  formatDuration,
  statusTagType,
  validateImage
} from './aid'

describe('statusTagType', () => {
  it('把状态码映射到 el-tag 语义色', () => {
    expect(statusTagType('COMPLETED')).toBe('success')
    expect(statusTagType('RUNNING')).toBe('warning')
    expect(statusTagType('FAILED')).toBe('danger')
    expect(statusTagType('DRAFT')).toBe('info')
  })

  it('未知或缺失值回落 info，避免新增枚举时整列渲染失败', () => {
    expect(statusTagType('NOT_A_STATUS')).toBe('info')
    expect(statusTagType(undefined)).toBe('info')
  })
})

describe('formatDuration', () => {
  it('秒转 mm:ss', () => {
    expect(formatDuration(0)).toBe('-')
    expect(formatDuration(-1)).toBe('-')
    expect(formatDuration(undefined)).toBe('-')
    expect(formatDuration(65)).toBe('01:05')
    expect(formatDuration(600)).toBe('10:00')
  })
})

describe('validateImage', () => {
  const image = (name: string, size: number) =>
    ({ name, size, type: 'image/png' }) as unknown as File

  it('接受白名单内的图片', () => {
    expect(validateImage(image('a.png', 1024))).toBe('')
    expect(validateImage(image('我的参考图.JPG', 1024))).toBe('')
  })

  it('拒绝非图片扩展名与无扩展名', () => {
    expect(validateImage(image('evil.jsp', 1024))).toContain('不支持的图片格式')
    expect(validateImage(image('noext', 1024))).toContain('不支持的图片格式')
  })

  it('拒绝空文件与超限文件', () => {
    expect(validateImage(image('a.png', 0))).toContain('文件为空')
    expect(
      validateImage(image('big.png', MAX_IMAGE_SIZE_MB * 1024 * 1024 + 1))
    ).toContain('不能超过')
  })

  it('扩展名大小写归一后仍走白名单', () => {
    expect(fileExtension('A.PNG')).toBe('png')
    expect(ALLOWED_IMAGE_EXTENSIONS).toContain(fileExtension('A.PNG'))
  })
})
