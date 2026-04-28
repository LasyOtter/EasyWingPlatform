import axios, { type AxiosRequestConfig } from 'axios'

/**
 * Axios 取消请求管理
 */
export class AxiosCanceler {
  private pendingMap: Map<string, AbortController>

  constructor() {
    this.pendingMap = new Map()
  }

  /**
   * 添加请求到待取消队列
   */
  addPending(config: AxiosRequestConfig) {
    this.removePending(config)
    const url = this.getPendingUrl(config)
    const controller = new AbortController()
    config.signal = controller.signal
    this.pendingMap.set(url, controller)
  }

  /**
   * 从待取消队列中移除请求
   */
  removePending(config: AxiosRequestConfig) {
    const url = this.getPendingUrl(config)
    const controller = this.pendingMap.get(url)
    if (controller) {
      controller.abort()
      this.pendingMap.delete(url)
    }
  }

  /**
   * 清空所有待处理的请求
   */
  removeAllPending() {
    this.pendingMap.forEach((controller) => {
      controller.abort()
    })
    this.pendingMap.clear()
  }

  /**
   * 重置取消器
   */
  reset() {
    this.removeAllPending()
  }

  /**
   * 获取请求的唯一标识
   */
  private getPendingUrl(config: AxiosRequestConfig): string {
    const { method, url, params, data } = config
    return [
      method,
      url,
      JSON.stringify(params || {}),
      JSON.stringify(data || {})
    ].join('&')
  }
}

export default AxiosCanceler
