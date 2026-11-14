// 性能监控集成 - Web Vitals
// 安装: pnpm add web-vitals

import { onCLS, onFID, onLCP, onFCP, onTTFB, onINP, type Metric } from 'web-vitals'

/**
 * 性能指标上报接口
 */
interface PerformanceReport {
  name: string
  value: number
  rating: 'good' | 'needs-improvement' | 'poor'
  delta: number
  id: string
  navigationType: string
}

/**
 * 性能监控配置
 */
const PERFORMANCE_CONFIG = {
  // 是否启用性能监控
  enabled: import.meta.env.VITE_PERF_MONITOR_ENABLED === 'true',
  // 上报 API 端点
  reportUrl: import.meta.env.VITE_PERF_REPORT_URL || '/api/monitor/performance',
  // 采样率（0-1）
  sampleRate: parseFloat(import.meta.env.VITE_PERF_SAMPLE_RATE || '1.0'),
  // 批量上报间隔（毫秒）
  batchInterval: 5000,
  // 批量上报最大数量
  batchSize: 10
}

/**
 * 性能指标缓冲区
 */
let metricsBuffer: PerformanceReport[] = []
let batchTimer: number | null = null

/**
 * 上报性能指标到服务器
 */
async function reportMetrics(metrics: PerformanceReport[]) {
  if (metrics.length === 0) return

  try {
    await fetch(PERFORMANCE_CONFIG.reportUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        metrics,
        timestamp: Date.now(),
        userAgent: navigator.userAgent,
        url: location.href,
        referrer: document.referrer
      }),
      // 使用 keepalive 确保页面卸载时也能发送
      keepalive: true
    })
  } catch (error) {
    console.error('Failed to report performance metrics:', error)
  }
}

/**
 * 批量上报性能指标
 */
function batchReportMetrics(metric: PerformanceReport) {
  metricsBuffer.push(metric)

  // 达到批量大小，立即上报
  if (metricsBuffer.length >= PERFORMANCE_CONFIG.batchSize) {
    if (batchTimer) {
      clearTimeout(batchTimer)
      batchTimer = null
    }
    reportMetrics([...metricsBuffer])
    metricsBuffer = []
    return
  }

  // 启动批量上报定时器
  if (!batchTimer) {
    batchTimer = window.setTimeout(() => {
      reportMetrics([...metricsBuffer])
      metricsBuffer = []
      batchTimer = null
    }, PERFORMANCE_CONFIG.batchInterval)
  }
}

/**
 * 处理性能指标
 */
function handleMetric(metric: Metric) {
  // 采样控制
  if (Math.random() > PERFORMANCE_CONFIG.sampleRate) {
    return
  }

  const report: PerformanceReport = {
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    delta: metric.delta,
    id: metric.id,
    navigationType: metric.navigationType || 'unknown'
  }

  // 控制台输出（开发环境）
  if (import.meta.env.DEV) {
    console.log(
      `[Performance] ${metric.name}:`,
      `${metric.value.toFixed(2)}ms`,
      `(${metric.rating})`
    )
  }

  // 批量上报
  batchReportMetrics(report)
}

/**
 * 初始化性能监控
 */
export function initPerformanceMonitoring() {
  if (!PERFORMANCE_CONFIG.enabled) {
    console.log('[Performance] Monitoring is disabled')
    return
  }

  console.log('[Performance] Monitoring initialized with sample rate:', PERFORMANCE_CONFIG.sampleRate)

  // Core Web Vitals
  onCLS(handleMetric)  // Cumulative Layout Shift (累积布局偏移)
  onFID(handleMetric)  // First Input Delay (首次输入延迟)
  onLCP(handleMetric)  // Largest Contentful Paint (最大内容绘制)

  // 其他性能指标
  onFCP(handleMetric)  // First Contentful Paint (首次内容绘制)
  onTTFB(handleMetric) // Time to First Byte (首字节时间)
  onINP(handleMetric)  // Interaction to Next Paint (交互到下次绘制)

  // 页面卸载时上报剩余指标
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      if (metricsBuffer.length > 0) {
        reportMetrics([...metricsBuffer])
        metricsBuffer = []
      }
    }
  })

  // 页面卸载时上报
  window.addEventListener('beforeunload', () => {
    if (metricsBuffer.length > 0) {
      reportMetrics([...metricsBuffer])
      metricsBuffer = []
    }
  })
}

/**
 * 自定义性能指标上报
 */
export function reportCustomMetric(name: string, value: number, metadata?: Record<string, any>) {
  if (!PERFORMANCE_CONFIG.enabled) return

  const report: PerformanceReport = {
    name: `custom_${name}`,
    value,
    rating: 'good',
    delta: value,
    id: `${Date.now()}-${Math.random()}`,
    navigationType: 'custom'
  }

  if (import.meta.env.DEV) {
    console.log(`[Performance] Custom metric ${name}:`, value, metadata)
  }

  batchReportMetrics(report)
}

/**
 * 性能标记工具
 */
export class PerformanceTimer {
  private startTime: number
  private name: string

  constructor(name: string) {
    this.name = name
    this.startTime = performance.now()
  }

  end(metadata?: Record<string, any>) {
    const duration = performance.now() - this.startTime
    reportCustomMetric(this.name, duration, metadata)
    return duration
  }
}

/**
 * 性能装饰器（用于 Vue 组件方法）
 */
export function measurePerformance(target: any, propertyKey: string, descriptor: PropertyDescriptor) {
  const originalMethod = descriptor.value

  descriptor.value = async function (...args: any[]) {
    const timer = new PerformanceTimer(propertyKey)
    try {
      const result = await originalMethod.apply(this, args)
      timer.end()
      return result
    } catch (error) {
      timer.end({ error: true })
      throw error
    }
  }

  return descriptor
}
