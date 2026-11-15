import { describe, expect, it } from 'vitest'
import { formatDate, formatRelativeTime, getDateRange } from './date'

describe('date utils', () => {
  it('formatDate 按模板格式化', () => {
    const date = new Date(2026, 0, 5, 9, 8, 7)
    expect(formatDate(date, 'YYYY-MM-DD HH:mm:ss')).toBe('2026-01-05 09:08:07')
    expect(formatDate(date, 'YYYY/MM/DD')).toBe('2026/01/05')
  })

  it('formatDate 对无效日期返回空串', () => {
    expect(formatDate('not-a-date')).toBe('')
  })

  it('formatRelativeTime 返回相对时间', () => {
    const now = Date.now()
    expect(formatRelativeTime(now - 30 * 1000)).toBe('刚刚')
    expect(formatRelativeTime(now - 5 * 60 * 1000)).toBe('5分钟前')
    expect(formatRelativeTime(now - 3 * 60 * 60 * 1000)).toBe('3小时前')
    expect(formatRelativeTime(now - 2 * 24 * 60 * 60 * 1000)).toBe('2天前')
    expect(formatRelativeTime('invalid')).toBe('')
  })

  it('getDateRange 返回起止时间', () => {
    const [start, end] = getDateRange('today')
    expect(start.getHours()).toBe(0)
    expect(start.getMinutes()).toBe(0)
    expect(end.getHours()).toBe(23)
    expect(end.getMinutes()).toBe(59)
    expect(start.getTime()).toBeLessThan(end.getTime())
  })
})
