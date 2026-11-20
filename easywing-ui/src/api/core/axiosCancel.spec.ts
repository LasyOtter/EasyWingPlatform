import { describe, expect, it } from 'vitest'
import AxiosCanceler from './axiosCancel'
import type { AxiosRequestConfig } from 'axios'

const base = (): AxiosRequestConfig => ({ method: 'get', url: '/api/test' })

describe('AxiosCanceler', () => {
  it('addPending 写入 signal', () => {
    const canceler = new AxiosCanceler()
    const config = base()

    canceler.addPending(config)

    expect(config.signal).toBeInstanceOf(AbortSignal)
    expect(config.signal?.aborted).toBe(false)
  })

  it('相同 method+url+params+data 的后一次请求会取消前一次', () => {
    const canceler = new AxiosCanceler()
    const first = base()
    const second = base()

    canceler.addPending(first)
    const firstSignal = first.signal
    canceler.addPending(second)

    expect(firstSignal?.aborted).toBe(true)
    expect(second.signal?.aborted).toBe(false)
  })

  it('removePending 中止并移除指定请求', () => {
    const canceler = new AxiosCanceler()
    const config = base()
    canceler.addPending(config)

    canceler.removePending(config)

    expect(config.signal?.aborted).toBe(true)
  })

  it('removePending 对未记录的请求不抛错', () => {
    const canceler = new AxiosCanceler()

    expect(() => canceler.removePending(base())).not.toThrow()
  })

  it('不同 params 视为不同请求，互不取消', () => {
    const canceler = new AxiosCanceler()
    const a: AxiosRequestConfig = { method: 'get', url: '/api/x', params: { id: 1 } }
    const b: AxiosRequestConfig = { method: 'get', url: '/api/x', params: { id: 2 } }

    canceler.addPending(a)
    canceler.addPending(b)

    expect(a.signal?.aborted).toBe(false)
    expect(b.signal?.aborted).toBe(false)
  })

  it('不同 method 视为不同请求', () => {
    const canceler = new AxiosCanceler()
    const get: AxiosRequestConfig = { method: 'get', url: '/api/x' }
    const post: AxiosRequestConfig = { method: 'post', url: '/api/x' }

    canceler.addPending(get)
    canceler.addPending(post)

    expect(get.signal?.aborted).toBe(false)
    expect(post.signal?.aborted).toBe(false)
  })

  it('removeAllPending 中止全部在途请求', () => {
    const canceler = new AxiosCanceler()
    const a = base()
    const b: AxiosRequestConfig = { method: 'get', url: '/api/other' }
    canceler.addPending(a)
    canceler.addPending(b)

    canceler.removeAllPending()

    expect(a.signal?.aborted).toBe(true)
    expect(b.signal?.aborted).toBe(true)
  })

  it('reset 等价于 removeAllPending', () => {
    const canceler = new AxiosCanceler()
    const a = base()
    canceler.addPending(a)

    canceler.reset()

    expect(a.signal?.aborted).toBe(true)
  })

  it('post 请求体参与唯一标识', () => {
    const canceler = new AxiosCanceler()
    const a: AxiosRequestConfig = { method: 'post', url: '/api/x', data: { id: 1 } }
    const b: AxiosRequestConfig = { method: 'post', url: '/api/x', data: { id: 1 } }
    const c: AxiosRequestConfig = { method: 'post', url: '/api/x', data: { id: 2 } }

    canceler.addPending(a)
    canceler.addPending(b)
    canceler.addPending(c)

    // b 与 a 同标识 -> a 被取消；c 标识不同 -> 不影响 b
    expect(a.signal?.aborted).toBe(true)
    expect(b.signal?.aborted).toBe(false)
    expect(c.signal?.aborted).toBe(false)
  })
})
